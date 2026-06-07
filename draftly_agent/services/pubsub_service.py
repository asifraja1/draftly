"""
Kafka pub/sub wrapper.

Producer  → publish(topic, payload)
Consumer  → subscribe_in_thread(topic, handler)

Each agent runs its own consumer in a daemon thread.
Consumer group IDs are set per-topic so each agent gets every message
independently (no competing consumers unless you scale horizontally).

Message format: UTF-8 JSON string.
"""
from __future__ import annotations
import json
import threading
from typing import Callable

from confluent_kafka import Producer, Consumer, KafkaError, KafkaException
from config import config


# ── Producer (shared, thread-safe) ────────────────────────────────────────────

_producer: Producer | None = None


def _get_producer() -> Producer:
    global _producer
    if _producer is None:
        _producer = Producer({
            "bootstrap.servers": config.KAFKA_BOOTSTRAP_SERVERS,
            "message.timeout.ms": 30000,
        })
    return _producer


def _delivery_report(err, msg):
    if err is not None:
        print(f"[Kafka] ❌ DELIVERY FAILED topic={msg.topic()} error={err}")
    else:
        print(f"[Kafka] ✅ delivered topic={msg.topic()} partition={msg.partition()} offset={msg.offset()}")


def publish(topic: str, payload: dict) -> None:
    """Serialize payload to JSON and produce to the given Kafka topic.

    Raises RuntimeError if the message is not actually delivered to the broker,
    so callers don't falsely report success.
    """
    producer = _get_producer()
    producer.produce(
        topic,
        value=json.dumps(payload).encode("utf-8"),
        callback=_delivery_report,
    )
    remaining = producer.flush(30)   # block up to 30s for delivery + callbacks
    if remaining > 0:
        raise RuntimeError(f"Kafka publish to '{topic}' timed out: {remaining} message(s) not delivered")


# ── Consumer ──────────────────────────────────────────────────────────────────

def subscribe_blocking(topic: str, handler: Callable[[dict], None]) -> None:
    """
    Blocks the calling thread, delivering each Kafka message to `handler`.
    Call from a daemon thread so it doesn't prevent shutdown.
    """
    consumer = Consumer({
        "bootstrap.servers":  config.KAFKA_BOOTSTRAP_SERVERS,
        "group.id":           f"draftly-{topic}",   # one group per topic/agent
        "auto.offset.reset":  "earliest",            # replay from start if no committed offset
        # LLM processing can take minutes. Give a generous poll interval so the
        # broker doesn't evict the consumer mid-processing (which caused dropped
        # output messages and reprocess loops).
        "max.poll.interval.ms": 1800000,             # 30 min
        "session.timeout.ms":   60000,               # 60s
        "heartbeat.interval.ms": 20000,              # 20s
        # Commit offsets ourselves, AFTER the handler finishes, so a message is
        # never marked done until its draft/summary has been published.
        "enable.auto.commit": False,
    })
    consumer.subscribe([topic])
    print(f"[Kafka] Subscribed to '{topic}' (group=draftly-{topic})")

    try:
        while True:
            msg = consumer.poll(timeout=1.0)
            if msg is None:
                continue
            if msg.error():
                if msg.error().code() == KafkaError._PARTITION_EOF:
                    continue
                print(f"[Kafka] Consumer error on '{topic}': {msg.error()}")
                continue
            try:
                payload = json.loads(msg.value().decode("utf-8"))
                handler(payload)
            except Exception as exc:
                print(f"[Kafka] Handler error on topic '{topic}': {exc}")
            finally:
                # Commit this message's offset whether or not the handler raised,
                # so we don't get stuck re-processing the same bad message forever.
                try:
                    consumer.commit(msg, asynchronous=False)
                except Exception as exc:
                    print(f"[Kafka] Commit error on '{topic}': {exc}")
    finally:
        consumer.close()


def subscribe_in_thread(topic: str, handler: Callable[[dict], None]) -> threading.Thread:
    """Starts subscribe_blocking in a daemon thread and returns it."""
    t = threading.Thread(
        target=subscribe_blocking,
        args=(topic, handler),
        daemon=True,
        name=f"kafka-consumer-{topic}",
    )
    t.start()
    return t
