import asyncio

from langchain_core.messages import HumanMessage
from langgraph.types import Command

from app.agent.approval import ApprovalMode, ModeState
from app.agent.builder import build_agent
from app.core.auth import login
from app.core.settings import load_settings


def _handle_mode(command: str, mode_state: ModeState) -> None:
    """Show or change the approval mode in response to a '/mode [auto|agent|ask]' line."""
    parts = command.split()
    if len(parts) == 1:
        print(f"   mode: {mode_state.mode.value}\n")
        return
    try:
        mode_state.mode = ApprovalMode(parts[1].strip().lower())
        print(f"   mode -> {mode_state.mode.value}\n")
    except ValueError:
        print("   usage: /mode auto|agent|ask\n")


def _collect_decisions(hitl_request: dict) -> list[dict]:
    """Ask the human about each pending write and return one decision per action, in order."""
    decisions: list[dict] = []
    for action in hitl_request["action_requests"]:
        print(f"\n  needs approval: {action['name']}")
        print(f"  args: {action['args']}")
        answer = input("  approve? [y/N] ").strip().lower()
        decisions.append({"type": "approve"} if answer == "y" else {"type": "reject"})
    return decisions


async def _run_turn(agent: object, payload: object, config: dict) -> dict:
    """Run one message and keep resolving approval pauses until the agent finishes."""
    result = await agent.ainvoke(payload, config=config)
    while result.get("__interrupt__"):
        hitl_request = result["__interrupt__"][0].value
        decisions = _collect_decisions(hitl_request)
        result = await agent.ainvoke(Command(resume={"decisions": decisions}), config=config)
    return result


async def main() -> None:
    settings = load_settings()
    print(f"Connecting to {settings.mcp_sse_url} as {settings.username} ...")
    # The REPL authenticates with the dev credentials; the HTTP server (next step) will
    # instead build the session from the identity Spring forwards.
    built = await build_agent(settings, login(settings))
    # One continuous, persistent conversation per user (matches the Java conversationId
    # convention "user-<id>"); reusing the same thread_id lets the agent remember.
    thread_id = f"user-{built.user_id}"
    print(
        f"Signed in as {built.role} — {len(built.tools)} of {built.total_tools} "
        f"tools available (conversation: {thread_id}, mode: {built.mode_state.mode.value}).\n"
        f"Commands: /mode [auto|agent|ask] to change approval, 'exit' or 'quit' to leave.\n"
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
        if user.lower().startswith("/mode"):
            _handle_mode(user, built.mode_state)
            continue

        result = await _run_turn(
            built.agent, {"messages": [HumanMessage(content=user)]}, config
        )
        print(f"\nai> {result['messages'][-1].content}\n")


if __name__ == "__main__":
    asyncio.run(main())
