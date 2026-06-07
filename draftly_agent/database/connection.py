import psycopg2
import psycopg2.extras
from contextlib import contextmanager
from config import config


def get_connection():
    return psycopg2.connect(
        config.DATABASE_URL,
        cursor_factory=psycopg2.extras.RealDictCursor,
    )


@contextmanager
def db_cursor():
    conn = get_connection()
    try:
        with conn.cursor() as cur:
            yield cur
        conn.commit()
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


def run_migrations(sql_path: str = "database/migrations.sql"):
    with open(sql_path) as f:
        sql = f.read()
    with db_cursor() as cur:
        cur.execute(sql)
    print("[DB] Migrations applied.")
