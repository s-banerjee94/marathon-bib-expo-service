"""Delete a stored agent conversation (all its checkpoints) from DynamoDB.

Use this to start a user's chat fresh — for example after an approval interrupt was
abandoned (a new message sent instead of /resume), which leaves a dangling tool call
that OpenAI then rejects. Run from the ai-agent/ directory:

    uv run python reset_chat.py user-1     # delete one conversation by thread id
    uv run python reset_chat.py 1          # same, given just the user id
"""

import asyncio
import sys

import boto3
from langgraph_checkpoint_aws import DynamoDBSaver

from settings import load_settings


async def _delete(thread_id: str) -> None:
    settings = load_settings()
    session = boto3.Session(region_name=settings.aws_region, profile_name=settings.aws_profile)
    saver = DynamoDBSaver(
        table_name=settings.checkpoint_table,
        session=session,
        endpoint_url=settings.ddb_endpoint_url,
    )
    await saver.adelete_thread(thread_id)
    print(f"Deleted conversation {thread_id} from {settings.checkpoint_table}.")


def main() -> None:
    if len(sys.argv) != 2:
        print("usage: uv run python reset_chat.py <thread-id|user-id>")
        raise SystemExit(2)
    arg = sys.argv[1]
    thread_id = arg if arg.startswith("user-") else f"user-{arg}"
    asyncio.run(_delete(thread_id))


if __name__ == "__main__":
    main()
