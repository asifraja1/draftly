"""
Entry point — starts both agents and keeps the process alive.

Usage:
    python main.py

Optional flags:
    --migrate    Run database migrations before starting
    --demo       Publish sample messages so you can watch the agents in action
"""
import argparse
import signal
import sys
import time

from agents.email_saving_agent import EmailSavingAgent
from agents.draft_email_agent import DraftEmailAgent
from services.pubsub_service import publish
from config import config


def run_demo():
    """Publish one saving message and two draft messages for smoke-testing."""
    print("\n[Demo] Publishing sample messages …\n")

    # Saving agent sample
    publish(config.TOPIC_SAVING_EMAIL, {
        "user_id":   "user_001",
        "thread_id": "thread_demo_001",
        "relation":  "Existing Customer",
        "from":      "alice@acme.com",
        "subject":   "Re: Q3 invoice",
        "body": (
            "Hi,\n\nThanks for sending over the invoice. "
            "Could you also include the breakdown by line item? "
            "We need it for our accounting department.\n\nBest,\nAlice"
        ),
    })

    time.sleep(1)

    # Draft agent — should trigger draft (question)
    publish(config.TOPIC_DRAFT_EMAIL, {
        "user_id":   "user_001",
        "thread_id": "thread_demo_001",
        "relation":  "Existing Customer",
        "from":      "alice@acme.com",
        "subject":   "Re: Q3 invoice",
        "body": (
            "Hi,\n\nThanks for sending over the invoice. "
            "Could you also include the breakdown by line item? "
            "We need it for our accounting department.\n\nBest,\nAlice"
        ),
    })

    time.sleep(1)

    # Draft agent — should trigger summary (newsletter)
    publish(config.TOPIC_DRAFT_EMAIL, {
        "user_id":   "user_001",
        "thread_id": "thread_demo_002",
        "relation":  "Unknown",
        "from":      "newsletter@techdigest.io",
        "subject":   "This week in AI — issue #47",
        "body": (
            "Welcome to this week's AI digest. "
            "Top stories: GPT-5 benchmarks, new open-source models, "
            "and a deep dive into retrieval-augmented generation."
        ),
    })

    print("[Demo] Messages published.\n")


def main():
    parser = argparse.ArgumentParser(description="Draftly Agent System")
    parser.add_argument("--migrate", action="store_true", help="Run DB migrations first")
    parser.add_argument("--demo",    action="store_true", help="Publish demo messages")
    args = parser.parse_args()

    if args.migrate:
        from database.connection import run_migrations
        run_migrations()

    saving_agent = EmailSavingAgent()
    draft_agent  = DraftEmailAgent()

    saving_agent.start()
    draft_agent.start()

    if args.demo:
        time.sleep(0.5)          # give subscriptions a moment to register
        run_demo()

    # Keep process alive; let the daemon subscriber threads do the work
    def _shutdown(sig, frame):
        print("\n[Main] Shutting down …")
        sys.exit(0)

    signal.signal(signal.SIGINT,  _shutdown)
    signal.signal(signal.SIGTERM, _shutdown)

    print("[Main] Both agents running. Press Ctrl+C to stop.\n")
    while True:
        time.sleep(1)


if __name__ == "__main__":
    main()
