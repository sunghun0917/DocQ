import os
from dotenv import load_dotenv
from openai import OpenAI, AsyncOpenAI

load_dotenv()

client = OpenAI(
    api_key=os.getenv("OPENAI_API_KEY"),
    base_url=os.getenv("OPENAI_BASE_URL", "https://gms.ssafy.io/gmsapi/api.openai.com/v1"),
)

aclient = AsyncOpenAI(
    api_key=os.getenv("OPENAI_API_KEY"),
    base_url=os.getenv("OPENAI_BASE_URL", "https://gms.ssafy.io/gmsapi/api.openai.com/v1"),
)


def call_llm(prompt: str) -> str:
    response = client.chat.completions.create(
        model="gpt-4.1-nano",
        messages=[
            {"role": "user", "content": prompt}
        ],
    )

    return response.choices[0].message.content


async def acall_llm(prompt: str) -> str:
    response = await aclient.chat.completions.create(
        model="gpt-4.1-nano",
       messages=[
            {"role": "user", "content": prompt}
        ],
    )

    return response.choices[0].message.content
