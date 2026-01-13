from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from typing import List
from app.faiss_utils import create_index, load_index, search

app = FastAPI(title="FAISS Vector Search Service")


class AddVectorsRequest(BaseModel):
    ids: List[int]
    vectors: List[List[float]]


class SearchRequest(BaseModel):
    qvector: List[float]
    top_k: int


class SearchResponse(BaseModel):
    ids: List[int]
    scores: List[float]


@app.get("/")
async def root():
    return {"message": "FAISS Vector Search Service"}


@app.post("/index/add")
async def add_vectors(request: AddVectorsRequest):
    """Add vectors to the FAISS index"""
    if len(request.ids) != len(request.vectors):
        raise HTTPException(
            status_code=400, detail="ids and vectors must have the same length"
        )

    create_index(request.ids, request.vectors)

    return {"status": "success", "added_count": len(request.ids)}


@app.post("/index/search", response_model=SearchResponse)
async def search_vectors(request: SearchRequest):
    scores, ids = search(request.qvector, request.top_k)
    return SearchResponse(
        ids=ids[0].tolist() if ids is not None else [],
        scores=scores[0].tolist() if scores is not None else [],
    )


@app.post("/index/reload", status_code=200)
async def reload_index():
    try:
        load_index()
        return {"status": "success", "message": "FAISS index reloaded"}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Error reloading index: {e}")


@app.on_event("startup")
async def startup_event():
    try:
        load_index()
    except Exception as e:
        print(f"Error loading FAISS index on startup: {e}")
