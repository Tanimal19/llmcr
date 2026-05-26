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


class RemoveVectorsRequest(BaseModel):
    index_name: str
    ids: List[int]


class RemoveVectorsResponse(BaseModel):
    status: str
    removed_count: int


@app.get("/")
async def root():
    return {
        "message": "FAISS Vector Search Service",
        "indexes": list_indexes_with_counts(),
    }


@app.post("/index/add_ids", response_model=AddVectorsResponse)
async def add_vectors_endpoint(request: AddVectorsRequest):
    try:
        add_index_ids(request.index_name, request.ids, request.vectors)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    return AddVectorsResponse(status="success", added_count=len(request.ids))


@app.post("/index/search_ids", response_model=SearchResponse)
async def search_vectors_endpoint(request: SearchRequest):
    try:
        scores, ids = search(request.index_name, request.qvector, request.top_k)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    return SearchResponse(ids=ids, scores=scores)


@app.post("/index/remove")
async def remove_index_endpoint(request: RemoveIndexRequest):
    try:
        return remove_index(request.index_name)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


@app.post("/index/remove_ids", response_model=RemoveVectorsResponse)
async def remove_vectors_endpoint(request: RemoveVectorsRequest):
    try:
        remove_index_ids(request.index_name, request.ids)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    return RemoveVectorsResponse(status="success", removed_count=len(request.ids))
