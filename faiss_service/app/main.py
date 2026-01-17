from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from typing import List
from app.faiss_utils import *

app = FastAPI(title="FAISS Vector Search Service")


class AddVectorsRequest(BaseModel):
    index_name: str
    ids: List[int]
    vectors: List[List[float]]


class AddVectorsResponse(BaseModel):
    status: str
    added_count: int


class SearchRequest(BaseModel):
    index_name: str
    qvector: List[float]
    top_k: int


class SearchResponse(BaseModel):
    ids: List[int]
    scores: List[float]


class RemoveIndexRequest(BaseModel):
    index_name: str


@app.get("/")
async def root():
    return {"message": "FAISS Vector Search Service"}


@app.post("/index/add", response_model=AddVectorsResponse)
async def add_vectors_endpoint(request: AddVectorsRequest):
    add_index(request.index_name, request.ids, request.vectors)
    return AddVectorsResponse(status="success", added_count=len(request.ids))


@app.post("/index/search", response_model=SearchResponse)
async def search_vectors_endpoint(request: SearchRequest):
    scores, ids = search(request.index_name, request.qvector, request.top_k)
    return SearchResponse(ids=ids, scores=scores)


@app.post("/index/remove")
async def remove_index_endpoint(request: RemoveIndexRequest):
    return remove_index(request.index_name)
