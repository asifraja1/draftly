# Draftly — AI-Powered Email Assistant

Draftly is a full-stack AI email assistant that connects to your Gmail, learns your writing style from sent emails, and automatically drafts replies when new emails arrive — in real time.

---

## Architecture Overview

```
┌─────────────────┐     OAuth2 + Gmail API     ┌──────────────────────┐
│  React Frontend │ ◄──────────────────────── ▶│  Spring Boot Backend │
│  (Vite + TS)    │     REST + SSE (real-time)  │  (Java 17)           │
└─────────────────┘                             └──────────┬───────────┘
                                                           │
                                              Kafka Topics │
                                    ┌──────────────────────┼──────────────────────┐
                                    │                      │                      │
                               saving_email           draft_email           output_email
                                    │                      │                      │
                                    ▼                      ▼                      │
                            ┌───────────────────────────────────┐                 │
                            │     Python LangGraph Agent        │ ────────────────┘
                            │  EmailSavingAgent + DraftAgent    │
                            └───────────────────────────────────┘
                                    │                      │
                              ChromaDB (vectors)    Postgres (styles)
```

---

## Features

- **Google OAuth2 Login** — secure login with Gmail, access + refresh tokens stored
- **Training Flow** — fetches last 50 sent emails, embeds them with OpenAI, stores vectors in ChromaDB
- **Real-Time Drafting** — Gmail Pub/Sub webhook detects new emails, LangGraph agent drafts replies
- **Style Matching** — cosine similarity search finds your most similar past email and mirrors its style
- **Human-in-the-Loop (HITL)** — for personal decisions (job offers, event invites), shows a decision card and waits for your input
- **gRPC Thread Context** — Python agent calls Java via gRPC to fetch full email thread history before drafting
- **Continuous Learning** — every email you send trains the model automatically
- **Multi-User Support** — all data is scoped per user via composite keys
- **SSE Push** — drafts appear instantly in the browser without polling
- **Email Summaries** — newsletters and notifications are summarised instead of drafted

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Frontend | React 18, TypeScript, Vite, Tailwind CSS, TanStack Query |
| Backend | Spring Boot 3, Java 17, Spring Security OAuth2 |
| Agent | Python 3.9, LangGraph, LangChain, OpenAI GPT-4o |
| Messaging | Apache Kafka (3 topics) |
| Vector DB | ChromaDB (cosine similarity, OpenAI text-embedding-3-small) |
| Database | PostgreSQL |
| RPC | gRPC (Java server ↔ Python client) |
| Email | Gmail API, Google Pub/Sub webhooks |

---

## Project Structure

```
draftly/                        ← Spring Boot Java backend
├── src/main/java/com/example/draftly/
│   ├── controller/             ← REST endpoints
│   │   ├── AuthController.java
│   │   ├── DraftController.java
│   │   ├── EmailBatchController.java
│   │   ├── GmailWebhookController.java
│   │   ├── RelationController.java
│   │   ├── SseController.java
│   │   └── SummaryController.java
│   ├── service/                ← Business logic
│   │   ├── DraftService.java
│   │   ├── EmailBatchProcessService.java
│   │   ├── GmailHistoryService.java
│   │   ├── GmailAccountService.java
│   │   ├── GmailSendService.java
│   │   ├── GmailWatchService.java
│   │   ├── KafkaProducerService.java
│   │   ├── OutputTopicConsumerService.java
│   │   └── SseEmitterService.java
│   ├── grpc/                   ← gRPC server
│   │   ├── EmailFetcherServiceImpl.java
│   │   └── GrpcServerRunner.java
│   ├── entity/                 ← JPA entities
│   ├── repository/             ← Spring Data JPA
│   ├── config/                 ← Security, OAuth2, Kafka, Gmail
│   └── dto/                    ← Kafka message DTOs

draftly_agent/                  ← Python LangGraph agent
├── main.py                     ← Entry point
├── agents/
│   ├── email_saving_agent.py   ← Subscribes to saving_email topic
│   └── draft_email_agent.py    ← Subscribes to draft_email topic
├── graphs/
│   ├── saving_graph.py         ← embed → ChromaDB → style profile
│   └── draft_graph.py          ← full decision tree with HITL
├── services/
│   ├── embedding_service.py    ← OpenAI text-embedding-3-small
│   ├── vector_store.py         ← ChromaDB wrapper
│   ├── writing_style_service.py← GPT style analysis
│   ├── grpc_client.py          ← gRPC client → Java server
│   └── pubsub_service.py       ← Kafka producer + consumer
└── proto/
    └── email_fetcher.proto     ← gRPC service definition

draftly-frontend/               ← React TypeScript frontend
├── src/
│   ├── pages/
│   │   ├── LoginPage.tsx
│   │   ├── TrainingPage.tsx
│   │   ├── DraftsPage.tsx
│   │   └── SummariesPage.tsx
│   ├── hooks/
│   │   └── useSse.ts           ← SSE real-time connection
│   └── api.ts                  ← All API calls
```

---

## How It Works

### Training Flow

```
User clicks "Fetch 50 Emails"
        ↓
Java fetches last 50 SENT emails from Gmail (in:sent)
        ↓
For each email with a known relation → publish to saving_email Kafka topic
        ↓
Python EmailSavingAgent:
  1. embed(body) → OpenAI → 1536-dim vector
  2. save to ChromaDB with metadata (user_id, relation, thread_id)
  3. GPT analyses writing style → saved to writing_styles table in Postgres
```

### Real-Time Drafting Flow

```
New email arrives in Gmail
        ↓
Gmail → Google Pub/Sub → POST /gmail/webhook
        ↓
Java GmailHistoryService:
  - decode Base64 notification
  - fetch Gmail History API (what changed since last historyId)
  - check labels: INBOX → draft flow, SENT → train
  - check relation exists for sender
  - publish to draft_email Kafka topic
        ↓
Python DraftEmailAgent (LangGraph):
  Node 1: draft or summary?
  Node 2: enough context in body?
  Node 3: fetch thread history via gRPC → Java
  Node 4: needs user decision? (job offer, event invite)
  Node 5: HITL → publish NEEDS_INPUT → wait for user
  Node 6: similarity search in ChromaDB
  Node 7: draft reply (embedding style or generic style)
  Node 8: publish to output_email Kafka topic
        ↓
Java OutputTopicConsumerService:
  - saves Draft/Summary to Postgres
  - SSE broadcast to all connected browser tabs
        ↓
Frontend auto-updates — draft appears instantly
```

### Human-in-the-Loop (HITL)

When the agent detects a personal decision email (job offer, event invitation, partnership proposal):
1. Publishes `NEEDS_INPUT` to output topic
2. Java saves draft with `status=NEEDS_INPUT`
3. SSE pushes decision card to frontend (question + options)
4. User clicks an option
5. Java re-publishes email to `draft_email` topic with `userDecision` set
6. Agent skips decision node → drafts reply using the decision

---

## Setup

### Prerequisites

- Java 17+
- Python 3.9+
- Node.js 18+
- PostgreSQL
- Apache Kafka
- ngrok (for Gmail webhook)
- Google Cloud project with Gmail API + Pub/Sub enabled

### 1. Java Backend

```bash
cd draftly

# Copy secrets template and fill in your credentials
cp secrets.properties.example secrets.properties
# Edit secrets.properties with your Google OAuth2 credentials

./gradlew bootRun
```

### 2. Python Agent

```bash
cd draftly_agent

python -m venv venv
source venv/bin/activate
pip install -r requirements.txt

# Copy env template and fill in your OpenAI key
cp .env.example .env
# Edit .env with your OpenAI API key

python main.py
```

### 3. Frontend

```bash
cd draftly-frontend

npm install
npm run dev
```

### 4. ngrok (for Gmail webhook)

```bash
ngrok http 8001
# Copy the HTTPS URL
# Update your Google Cloud Pub/Sub push subscription endpoint to:
# https://YOUR_NGROK_URL/gmail/webhook
```

---

## Environment Variables

### Java (`secrets.properties`)

```properties
GOOGLE_CLIENT_ID=your-google-client-id
GOOGLE_CLIENT_SECRET=your-google-client-secret
```

### Python (`.env`)

```
OPENAI_API_KEY=your-openai-api-key
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
CHROMA_PATH=./chroma_db
```

---

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/user` | Get logged-in user info |
| POST | `/api/emails/process` | Fetch & train on last 50 sent emails |
| GET | `/api/relations` | List user's relations |
| POST | `/api/relations` | Create/update a relation |
| GET | `/api/drafts` | List user's drafts |
| PUT | `/api/drafts/{id}` | Edit draft content |
| POST | `/api/drafts/{id}/send` | Send draft via Gmail |
| POST | `/api/drafts/{id}/decision` | Submit HITL decision |
| GET | `/api/summaries` | List email summaries |
| GET | `/api/events` | SSE stream for real-time updates |
| POST | `/gmail/webhook` | Gmail Pub/Sub push endpoint |

---

## Key Design Decisions

| Decision | Reason |
|----------|--------|
| Kafka for async messaging | Decouples Java and Python; HTTP request returns immediately while AI processes in background |
| SSE for real-time push | Drafts appear instantly without polling |
| gRPC for thread context | Type-safe, fast binary protocol between Java and Python |
| ChromaDB with metadata filtering | Single collection for all users; filtered by `(user_id, relation)` for isolation |
| Stateless HITL | No checkpointer needed; graph ends and re-runs with decision attached |
| Composite unique key on Relation | Two users can have the same contact email without conflict |
| `@Async` on Kafka publish | Never blocks the HTTP response thread |
| historyId always advanced | Prevents replay of same webhook messages on failures |

---

## Demo

Full end-to-end demo video included in project submission.
