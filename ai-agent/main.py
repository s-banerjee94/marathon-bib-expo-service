import asyncio

from langchain_core.messages import HumanMessage

from agent import build_agent
from settings import load_settings


async def main() -> None:
    settings = load_settings()
    print(f"Connecting to {settings.mcp_sse_url} as {settings.username} ...")
    built = await build_agent(settings)
    # One continuous, persistent conversation per user (matches the Java conversationId
    # convention "user-<id>"); reusing the same thread_id lets the agent remember.
    thread_id = f"user-{built.user_id}"
    print(
        f"Signed in as {built.role} — {len(built.tools)} of {built.total_tools} "
        f"tools available (conversation: {thread_id}). Type 'exit' or 'quit' to leave.\n"
    )

    config = {"configurable": {"thread_id": thread_id}}

    while True:
        try:
            user = input("you> ").strip()
        except (EOFError, KeyboardInterrupt):
            print()
            break
        if user.lower() in {"exit", "quit"}:
            break
        if not user:
            continue

        result = await built.agent.ainvoke(
            {"messages": [HumanMessage(content=user)]},
            config=config,
        )
        print(f"\nai> {result['messages'][-1].content}\n")


if __name__ == "__main__":
    asyncio.run(main())
