from dotenv import load_dotenv
import os

load_dotenv()


def _require(key: str) -> str:
    val = os.getenv(key)
    if not val:
        raise RuntimeError(f"Missing required env var: {key}")
    return val


class Config:
    OPENAI_API_KEY: str = _require("OPENAI_API_KEY")

    DATABASE_URL: str = _require("DATABASE_URL")

    KAFKA_BOOTSTRAP_SERVERS: str = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")

    TOPIC_SAVING_EMAIL:       str = os.getenv("TOPIC_SAVING_EMAIL",       "saving_email")
    TOPIC_DRAFT_EMAIL:        str = os.getenv("TOPIC_DRAFT_EMAIL",        "draft_email")
    TOPIC_OUTPUT:             str = os.getenv("TOPIC_OUTPUT",             "output_email")
    TOPIC_USER_INPUT_REQUEST: str = os.getenv("TOPIC_USER_INPUT_REQUEST", "user_input_request")
    TOPIC_USER_INPUT_RESPONSE:str = os.getenv("TOPIC_USER_INPUT_RESPONSE","user_input_response")

    EMBEDDING_MODEL: str = os.getenv("EMBEDDING_MODEL", "text-embedding-3-small")
    EMBEDDING_DIM: int = int(os.getenv("EMBEDDING_DIM", "1536"))

    LLM_MODEL: str = os.getenv("LLM_MODEL", "gpt-4o")
    LLM_TEMPERATURE: float = float(os.getenv("LLM_TEMPERATURE", "0"))

    # Percentage 0-100
    SIMILARITY_THRESHOLD: float = float(os.getenv("SIMILARITY_THRESHOLD", "80"))


config = Config()
