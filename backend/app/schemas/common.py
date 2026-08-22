from math import ceil
from typing import Generic, Sequence, TypeVar

from pydantic import BaseModel, ConfigDict, Field

T = TypeVar("T")


class ORMModel(BaseModel):
    model_config = ConfigDict(from_attributes=True)


class MessageResponse(BaseModel):
    message: str


class PageParams(BaseModel):
    page: int = Field(default=1, ge=1)
    page_size: int = Field(default=20, ge=1, le=100)

    @property
    def offset(self) -> int:
        return (self.page - 1) * self.page_size


class Page(BaseModel, Generic[T]):
    items: list[T]
    total: int
    page: int
    page_size: int
    pages: int

    @classmethod
    def build(cls, items: Sequence[T], total: int, params: PageParams) -> "Page[T]":
        return cls(
            items=list(items),
            total=total,
            page=params.page,
            page_size=params.page_size,
            pages=ceil(total / params.page_size) if total else 0,
        )
