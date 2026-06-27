"""Dump a stored agent conversation from DynamoDB as plain text.

The checkpoint table holds conversations as encoded state snapshots, not readable rows,
so this decodes them back into messages. Run from the ai-agent/ directory:

    uv run python read_chat.py            # list and print every stored conversation
    uv run python read_chat.py user-1     # print one conversation by thread id
    uv run python read_chat.py 1          # same, given just the user id
"""

import sys

import boto3
from langgraph_checkpoint_aws import DynamoDBSaver

from settings import load_settings

_CHECKPOINT_PK_PREFIX = "CHECKPOINT_"


def _thread_ids(ddb, table: str) -> list[str]:
    """Every conversation in the table, read from its CHECKPOINT_<thread_id> keys."""
    ids: set[str] = set()
    scan_kwargs = {"TableName": table}
    while True:
        resp = ddb.scan(**scan_kwargs)
        for item in resp.get("Items", []):
            pk = item["PK"]["S"]
            if pk.startswith(_CHECKPOINT_PK_PREFIX):
                ids.add(pk[len(_CHECKPOINT_PK_PREFIX):])
        if "LastEvaluatedKey" not in resp:
            break
        scan_kwargs["ExclusiveStartKey"] = resp["LastEvaluatedKey"]
    return sorted(ids)


def _print_conversation(saver: DynamoDBSaver, thread_id: str) -> None:
    state = saver.get_tuple({"configurable": {"thread_id": thread_id, "checkpoint_ns": ""}})
    print(f"\n===== {thread_id} =====")
    if state is None:
        print("(no conversation found)")
        return
    messages = state.checkpoint.get("channel_values", {}).get("messages", [])
    print(f"{len(messages)} messages:")
    for message in messages:
        role = type(message).__name__.replace("Message", "")
        content = getattr(message, "content", "")
        if isinstance(content, list):
            content = " ".join(str(part) for part in content)
        if content:
            print(f"  [{role}] {content}")
        for call in getattr(message, "tool_calls", None) or []:
            print(f"  [{role}->tool] {call.get('name')}({call.get('args')})")


def main() -> None:
    settings = load_settings()
    session = boto3.Session(region_name=settings.aws_region, profile_name=settings.aws_profile)
    ddb = session.client("dynamodb", endpoint_url=settings.ddb_endpoint_url)
    saver = DynamoDBSaver(
        table_name=settings.checkpoint_table,
        session=session,
        endpoint_url=settings.ddb_endpoint_url,
    )

    if len(sys.argv) > 1:
        arg = sys.argv[1]
        thread_ids = [arg if arg.startswith("user-") else f"user-{arg}"]
    else:
        thread_ids = _thread_ids(ddb, settings.checkpoint_table)
        if not thread_ids:
            print(f"No conversations in {settings.checkpoint_table}.")
            return
        print(f"{len(thread_ids)} conversation(s) in {settings.checkpoint_table}: {thread_ids}")

    for thread_id in thread_ids:
        _print_conversation(saver, thread_id)


if __name__ == "__main__":
    main()
