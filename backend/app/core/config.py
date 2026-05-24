from functools import lru_cache

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    app_name: str = "Investment Co-Pilot"
    environment: str = Field(default="local")
    debug: bool = Field(default=True)

    database_url: str = Field(
        default="postgresql+asyncpg://copilot:copilot@localhost:5432/copilot",
    )
    redis_url: str = Field(default="redis://localhost:6379/0")

    cors_origins: list[str] = Field(default_factory=lambda: ["http://localhost:3000"])

    quote_cache_ttl_seconds: int = 60


@lru_cache
def get_settings() -> Settings:
    return Settings()
