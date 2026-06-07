"""
gRPC client for fetching thread emails.

The server is expected to implement EmailFetcherService (proto/email_fetcher.proto).
GRPC_EMAIL_HOST and GRPC_EMAIL_PORT are read from .env.
"""
import os
import sys
import grpc

# Generated stubs live in grpc_generated/
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "grpc_generated"))
import email_fetcher_pb2 as pb2
import email_fetcher_pb2_grpc as pb2_grpc

from dotenv import load_dotenv

load_dotenv()

_HOST = os.getenv("GRPC_EMAIL_HOST", "localhost")
_PORT = os.getenv("GRPC_EMAIL_PORT", "50051")


def fetch_thread_emails(thread_id: str, user_id: str, limit: int = 5) -> list[dict]:
    """
    Returns up to `limit` emails for the given thread.
    Each email is a dict: {id, from, subject, body, timestamp}
    Returns an empty list on any error.
    """
    address = f"{_HOST}:{_PORT}"
    try:
        with grpc.insecure_channel(address) as channel:
            stub = pb2_grpc.EmailFetcherServiceStub(channel)
            request = pb2.ThreadRequest(
                thread_id=thread_id,
                user_id=user_id,
                limit=limit,
            )
            response = stub.GetThreadEmails(request, timeout=10)

            if response.error:
                print(f"[gRPC] Server error: {response.error}")
                return []

            return [
                {
                    "id":        e.id,
                    "from":      getattr(e, "from"),   # 'from' is a Python keyword
                    "subject":   e.subject,
                    "body":      e.body,
                    "timestamp": e.timestamp,
                }
                for e in response.emails
            ]
    except grpc.RpcError as exc:
        print(f"[gRPC] RPC failed ({exc.code()}): {exc.details()}")
        return []
    except Exception as exc:
        print(f"[gRPC] Unexpected error: {exc}")
        return []
