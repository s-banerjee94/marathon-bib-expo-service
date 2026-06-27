import asyncio
import json

from mcp import ClientSession
from mcp.client.sse import sse_client

from auth import login
from settings import load_settings


async def main() -> None:
    settings = load_settings()
    token = login(settings)
    headers = {"Authorization": f"Bearer {token}"}

    async with sse_client(settings.mcp_sse_url, headers=headers) as (read, write):
        async with ClientSession(read, write) as session:
            await session.initialize()
            tools = await session.list_tools()

            tool = next((t for t in tools.tools if t.name == "create_event"), None)
            if tool is None:
                print("create_event tool not found. Tools:", [t.name for t in tools.tools])
                return

            print("NAME:", tool.name)
            print("\nDESCRIPTION:\n", tool.description)
            print("\nINPUT SCHEMA:\n", json.dumps(tool.inputSchema, indent=2))


if __name__ == "__main__":
    asyncio.run(main())
