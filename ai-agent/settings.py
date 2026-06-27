import os
from dataclasses import dataclass

from dotenv import load_dotenv

# Read the .env file and copy its values into the environment, so os.getenv() can see them.
load_dotenv()


@dataclass(frozen=True)
class Settings:
    """All configuration the agent needs, read once from .env."""

    api_base_url: str   # where the Spring app runs
    mcp_sse_url: str    # the MCP server's SSE endpoint
    username: str       # dev login username
    password: str       # dev login password
    openai_model: str   # which OpenAI model to use
    summary_model: str  # cheaper model used to summarize old conversation history

    # Where the agent stores conversation memory (DynamoDB checkpoints).
    aws_region: str               # AWS region for the checkpoint table
    aws_profile: str | None       # local AWS CLI profile; None on EC2 (instance role)
    ddb_endpoint_url: str | None  # LocalStack endpoint; None for real AWS
    checkpoint_table: str         # DynamoDB table holding the conversation checkpoints


def load_settings() -> Settings:
    """Build a Settings object from environment variables, applying dev defaults."""
    api_base = os.getenv("BIBEXPO_API_BASE_URL", "http://localhost:8080").rstrip("/")
    return Settings(
        api_base_url=api_base,
        mcp_sse_url=os.getenv("BIBEXPO_MCP_SSE_URL", f"{api_base}/sse"),
        username=os.getenv("BIBEXPO_USERNAME", "root"),
        password=os.getenv("BIBEXPO_PASSWORD", "root"),
        openai_model=os.getenv("OPENAI_MODEL", "gpt-4.1"),
        summary_model=os.getenv("OPENAI_SUMMARY_MODEL", "gpt-4.1-mini"),
        aws_region=os.getenv("AWS_REGION", "ap-south-1"),
        aws_profile=os.getenv("AWS_PROFILE") or None,
        ddb_endpoint_url=os.getenv("BIBEXPO_DDB_ENDPOINT_URL") or None,
        checkpoint_table=os.getenv("BIBEXPO_CHECKPOINT_TABLE", "marathon-ai-agent-checkpoints"),
    )
