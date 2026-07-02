import os
from dataclasses import dataclass

from dotenv import load_dotenv

from ..agent.approval import ApprovalMode

# Read the .env file and copy its values into the environment, so os.getenv() can see them.
load_dotenv()


@dataclass(frozen=True)
class Settings:
    """All configuration the agent needs, read once from .env."""

    api_base_url: str   # where the Spring app runs
    mcp_sse_url: str    # the MCP server's SSE endpoint
    mcp_timeout_seconds: float  # HTTP timeout connecting to / calling the MCP server
    username: str       # dev login username
    password: str       # dev login password
    openai_model: str   # which OpenAI model to use
    summary_model: str  # cheaper model used to summarize old conversation history
    context_budget_tokens: int  # working token budget; summarization triggers at 75% of this

    # Where the agent stores conversation memory (DynamoDB checkpoints).
    aws_region: str               # AWS region for the checkpoint table
    aws_profile: str | None       # local AWS CLI profile; None on EC2 (instance role)
    ddb_endpoint_url: str | None  # LocalStack endpoint; None for real AWS
    checkpoint_table: str         # DynamoDB table holding the conversation checkpoints
    usage_table: str              # DynamoDB table holding per-user daily token usage

    approval_mode: ApprovalMode   # how freely the agent acts before asking a human

    # HTTP chat service (the browser calls this directly with a Bearer access token).
    api_host: str                 # interface uvicorn binds
    api_port: int                 # port uvicorn binds

    # User-token verification (RS256). The agent holds ONLY the public key: it can verify a user's
    # access token but cannot mint tokens. The PEM is the same public key the Spring app signs with.
    jwt_public_key_path: str      # path to the RSA public key PEM (verify-only)
    jwt_issuer: str               # expected 'iss' claim; must match Spring's jwt.issuer

    # Per-role daily token budgets (prompt + completion). Must mirror Spring's app.ai.agent.limits —
    # both processes read the same counter, so a user's cap has to be identical on each side. A role
    # absent here (e.g. DISTRIBUTOR) or a negative value means "no cap enforced here".
    ai_limits: dict[str, int]

    # Browser-direct CORS. The frontend calls this service cross-origin: in local dev from
    # http://localhost:<port>, and in prod from the Amplify site origin (different host than the api
    # subdomain). List the EXACT frontend origins; set your Amplify origin here in prod.
    cors_allowed_origins: list[str]


def _parse_mode(raw: str) -> ApprovalMode:
    """Turn the BIBEXPO_APPROVAL_MODE text into a mode, defaulting to AGENT if unknown."""
    try:
        return ApprovalMode(raw.strip().lower())
    except ValueError:
        return ApprovalMode.AGENT


def load_settings() -> Settings:
    """Build a Settings object from environment variables, applying dev defaults."""
    api_base = os.getenv("BIBEXPO_API_BASE_URL", "http://localhost:8080").rstrip("/")
    return Settings(
        api_base_url=api_base,
        mcp_sse_url=os.getenv("BIBEXPO_MCP_SSE_URL", f"{api_base}/sse"),
        mcp_timeout_seconds=float(os.getenv("BIBEXPO_MCP_TIMEOUT_SECONDS", "30")),
        username=os.getenv("BIBEXPO_USERNAME", "root"),
        password=os.getenv("BIBEXPO_PASSWORD", "root"),
        openai_model=os.getenv("OPENAI_MODEL", "gpt-4.1"),
        summary_model=os.getenv("OPENAI_SUMMARY_MODEL", "gpt-4.1-mini"),
        context_budget_tokens=int(os.getenv("BIBEXPO_CONTEXT_BUDGET_TOKENS", "16000")),
        aws_region=os.getenv("AWS_REGION", "ap-south-1"),
        aws_profile=os.getenv("AWS_PROFILE") or None,
        ddb_endpoint_url=os.getenv("BIBEXPO_DDB_ENDPOINT_URL") or None,
        checkpoint_table=os.getenv("BIBEXPO_CHECKPOINT_TABLE", "marathon-ai-agent-checkpoints"),
        usage_table=os.getenv("BIBEXPO_AI_USAGE_TABLE", "marathon-ai-usage"),
        approval_mode=_parse_mode(os.getenv("BIBEXPO_APPROVAL_MODE", "agent")),
        api_host=os.getenv("BIBEXPO_API_HOST", "127.0.0.1"),
        api_port=int(os.getenv("BIBEXPO_API_PORT", "8000")),
        jwt_public_key_path=os.getenv("BIBEXPO_JWT_PUBLIC_KEY_PATH", "../keys/jwt-public-dev.pem"),
        jwt_issuer=os.getenv("BIBEXPO_JWT_ISSUER", "marathon-bib-expo-service"),
        ai_limits={
            "ROOT": int(os.getenv("AI_LIMIT_ROOT", "1000000")),
            "ADMIN": int(os.getenv("AI_LIMIT_ADMIN", "300000")),
            "ORGANIZER_ADMIN": int(os.getenv("AI_LIMIT_ORGANIZER_ADMIN", "100000")),
            "ORGANIZER_USER": int(os.getenv("AI_LIMIT_ORGANIZER_USER", "100000")),
        },
        cors_allowed_origins=[
            o.strip()
            for o in os.getenv("BIBEXPO_CORS_ALLOWED_ORIGINS", "http://localhost:4200,http://localhost:3000").split(",")
            if o.strip()
        ],
    )


# Read once at import and shared by the FastAPI layer (main, dependencies, routers). The agent
# layer instead receives a Settings via build_agent, so the same builder also serves the dev REPL.
settings: Settings = load_settings()
