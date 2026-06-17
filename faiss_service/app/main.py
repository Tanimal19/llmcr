from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from typing import List, Optional
from app.faiss_utils import add_vectors, search, remove_vectors, load_index

app = FastAPI(title="FAISS Vector Search Service")


class AddVectorsRequest(BaseModel):
    ids: List[int]
    vectors: List[List[float]]


class AddVectorsResponse(BaseModel):
    status: str
    added_count: int


class SearchRequest(BaseModel):
    qvector: List[float]
    top_k: int
    allowed_ids: Optional[List[int]] = None


class SearchResponse(BaseModel):
    ids: List[int]
    scores: List[float]


class RemoveVectorsRequest(BaseModel):
    ids: List[int]


class RemoveVectorsResponse(BaseModel):
    status: str
    removed_count: int


@app.get("/")
async def root():
    index = load_index()
    return {
        "message": "FAISS Vector Search Service",
        "total_vectors": int(index.ntotal) if index else 0,
    }


@app.post("/vectors/add", response_model=AddVectorsResponse)
async def add_vectors_endpoint(request: AddVectorsRequest):
    try:
        add_vectors(request.ids, request.vectors)
    except Exception as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    return AddVectorsResponse(status="success", added_count=len(request.ids))


@app.post("/vectors/search", response_model=SearchResponse)
async def search_endpoint(request: SearchRequest):
    try:
        scores, ids = search(request.qvector, request.top_k, request.allowed_ids)
    except Exception as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    return SearchResponse(ids=ids, scores=scores)


@app.post("/vectors/remove", response_model=RemoveVectorsResponse)
async def remove_vectors_endpoint(request: RemoveVectorsRequest):
    try:
        removed = remove_vectors(request.ids)
    except Exception as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    return RemoveVectorsResponse(status="success", removed_count=removed)
