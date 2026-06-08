import json
import math
from pathlib import Path

import pandas as pd
import streamlit as st

DEFAULT_CONTEXT_DIR = Path(__file__).parent / "data" / "reviews" / "llmcr"


def _safe_int(value):
    if isinstance(value, bool):
        return 0
    if isinstance(value, (int, float)):
        return int(value)
    return None


def _pick_token_value(data: dict, keys: list[str]) -> int | None:
    for key in keys:
        value = _safe_int(data.get(key))
        if value is not None:
            return value
    return None


def _extract_text_fragments(payload, fragments: list[str], max_fragments: int = 400):
    if len(fragments) >= max_fragments:
        return

    if payload is None:
        return

    if isinstance(payload, str):
        if payload:
            fragments.append(payload)
        return

    if isinstance(payload, bytes):
        text = payload.decode("utf-8", errors="ignore")
        if text:
            fragments.append(text)
        return

    if isinstance(payload, list):
        for item in payload:
            _extract_text_fragments(item, fragments, max_fragments=max_fragments)
            if len(fragments) >= max_fragments:
                return
        return

    if isinstance(payload, dict):
        preferred_keys = [
            "text",
            "content",
            "message",
            "output",
            "input",
            "analysis",
            "finalAnswer",
            "innerThought",
        ]

        used_preferred = False
        for key in preferred_keys:
            if key in payload:
                used_preferred = True
                _extract_text_fragments(
                    payload.get(key), fragments, max_fragments=max_fragments
                )
                if len(fragments) >= max_fragments:
                    return

        if not used_preferred:
            for value in payload.values():
                _extract_text_fragments(value, fragments, max_fragments=max_fragments)
                if len(fragments) >= max_fragments:
                    return


def _estimate_tokens_from_payload(payload) -> int:
    fragments: list[str] = []
    _extract_text_fragments(payload, fragments)

    if not fragments:
        return 0

    text = "\n".join(fragments)
    char_count = len(text)
    if char_count <= 0:
        return 0

    return max(1, math.ceil(char_count / 4))


def extract_token_counts(
    node: dict, estimate_from_io: bool = True
) -> tuple[int, int, bool, bool, str]:
    input_keys = [
        "inputTokens",
        "inputToken",
        "inputTokenCount",
        "promptTokens",
        "promptTokenCount",
        "prompt_tokens",
    ]
    output_keys = [
        "outputTokens",
        "outputToken",
        "outputTokenCount",
        "completionTokens",
        "completionTokenCount",
        "completion_tokens",
    ]
    total_keys = ["totalTokens", "totalTokenCount", "total_tokens"]

    input_tokens = _pick_token_value(node, input_keys)
    output_tokens = _pick_token_value(node, output_keys)
    explicit_total_tokens = None

    usage_containers = [
        "usage",
        "tokenUsage",
        "token_usage",
        "usageMetadata",
        "metadata",
        "responseMetadata",
        "cost",
    ]
    for key in usage_containers:
        value = node.get(key)
        if isinstance(value, dict):
            if input_tokens is None:
                input_tokens = _pick_token_value(value, input_keys)
            if output_tokens is None:
                output_tokens = _pick_token_value(value, output_keys)

    if input_tokens is None:
        input_tokens = 0
    if output_tokens is None:
        output_tokens = 0

    if input_tokens == 0 and output_tokens == 0:
        total_tokens = _pick_token_value(node, total_keys)
        if total_tokens is None:
            for key in usage_containers:
                value = node.get(key)
                if isinstance(value, dict):
                    total_tokens = _pick_token_value(value, total_keys)
                    if total_tokens is not None:
                        break
        if total_tokens is not None:
            explicit_total_tokens = total_tokens
            input_tokens = total_tokens

    has_explicit_tokens = input_tokens > 0 or output_tokens > 0 or explicit_total_tokens

    has_estimated_tokens = False
    if (not has_explicit_tokens) and estimate_from_io:
        est_input = _estimate_tokens_from_payload(node.get("input"))
        est_output = _estimate_tokens_from_payload(node.get("output"))
        input_tokens = est_input
        output_tokens = est_output
        has_estimated_tokens = est_input > 0 or est_output > 0

    if has_explicit_tokens:
        token_source = "explicit"
    elif has_estimated_tokens:
        token_source = "estimated"
    else:
        token_source = "none"

    return (
        input_tokens,
        output_tokens,
        bool(has_explicit_tokens),
        has_estimated_tokens,
        token_source,
    )


def parse_agent_context(path: Path) -> list[dict]:
    raw = path.read_text(encoding="utf-8").strip()
    if not raw:
        return []

    try:
        parsed = json.loads(raw)
        if isinstance(parsed, list):
            return [x for x in parsed if isinstance(x, dict)]
        if isinstance(parsed, dict):
            return [parsed]
    except json.JSONDecodeError:
        pass

    records: list[dict] = []
    for line in raw.splitlines():
        line = line.strip().strip(",")
        if not line or line in {"[", "]"}:
            continue
        if line.startswith("{") and line.endswith("}"):
            try:
                records.append(json.loads(line))
            except json.JSONDecodeError:
                continue
    return records


def collect_agent_context_files(root: Path) -> list[Path]:
    if root.is_file():
        return [root] if root.name == "agent_context.json" else []

    if not root.exists():
        return []

    return sorted(path for path in root.rglob("agent_context.json") if path.is_file())


def flatten_runs(
    records: list[dict], estimate_from_io: bool = True
) -> tuple[pd.DataFrame, pd.DataFrame, pd.DataFrame, pd.DataFrame, int]:
    node_rows: list[dict] = []
    edge_rows: list[dict] = []
    run_rows: list[dict] = []

    next_node_id = 1

    def walk(
        node: dict, run_id: int, parent_node_id: int | None = None, depth: int = 0
    ):
        nonlocal next_node_id

        current_id = next_node_id
        next_node_id += 1

        agent_name = str(node.get("agentName") or "UnknownAgent")
        model_name = str(node.get("modelName") or "")
        duration_ms = _safe_int(node.get("durationMs")) or 0
        started_at = _safe_int(node.get("startedAt"))
        ended_at = _safe_int(node.get("endedAt"))

        (
            input_tokens,
            output_tokens,
            has_explicit_tokens,
            has_estimated_tokens,
            token_source,
        ) = extract_token_counts(node, estimate_from_io=estimate_from_io)

        node_rows.append(
            {
                "node_id": current_id,
                "run_id": run_id,
                "parent_node_id": parent_node_id,
                "depth": depth,
                "agent": agent_name,
                "model": model_name,
                "duration_ms": duration_ms,
                "started_at": started_at,
                "ended_at": ended_at,
                "input_tokens": input_tokens,
                "output_tokens": output_tokens,
                "total_tokens": input_tokens + output_tokens,
                "has_explicit_tokens": has_explicit_tokens,
                "has_estimated_tokens": has_estimated_tokens,
                "token_source": token_source,
            }
        )

        if parent_node_id is not None:
            parent_agent = next(
                (
                    r["agent"]
                    for r in reversed(node_rows)
                    if r["node_id"] == parent_node_id
                ),
                "UnknownAgent",
            )
            edge_rows.append(
                {
                    "run_id": run_id,
                    "source_node_id": parent_node_id,
                    "target_node_id": current_id,
                    "source_agent": parent_agent,
                    "target_agent": agent_name,
                }
            )

        for child in node.get("iterationHistory", []) or []:
            if isinstance(child, dict):
                walk(child, run_id=run_id, parent_node_id=current_id, depth=depth + 1)

    for idx, record in enumerate(records, start=1):
        run_id = idx
        walk(record, run_id=run_id)

        run_nodes = [r for r in node_rows if r["run_id"] == run_id]
        run_rows.append(
            {
                "run_id": run_id,
                "root_agent": str(record.get("agentName") or "UnknownAgent"),
                "model": str(record.get("modelName") or ""),
                "duration_ms": _safe_int(record.get("durationMs")) or 0,
                "nodes": len(run_nodes),
                "input_tokens": sum(n["input_tokens"] for n in run_nodes),
                "output_tokens": sum(n["output_tokens"] for n in run_nodes),
                "total_tokens": sum(n["total_tokens"] for n in run_nodes),
                "explicit_token_nodes": sum(
                    1 for n in run_nodes if n["has_explicit_tokens"]
                ),
                "estimated_token_nodes": sum(
                    1 for n in run_nodes if n["has_estimated_tokens"]
                ),
            }
        )

    node_df = pd.DataFrame(node_rows)
    edge_df = pd.DataFrame(edge_rows)
    run_df = pd.DataFrame(run_rows)

    if node_df.empty:
        agent_df = pd.DataFrame(
            columns=[
                "agent",
                "calls",
                "input_tokens",
                "output_tokens",
                "total_tokens",
                "avg_duration_ms",
                "explicit_token_nodes",
                "estimated_token_nodes",
            ]
        )
    else:
        agent_df = (
            node_df.groupby("agent", as_index=False)
            .agg(
                calls=("node_id", "count"),
                input_tokens=("input_tokens", "sum"),
                output_tokens=("output_tokens", "sum"),
                total_tokens=("total_tokens", "sum"),
                avg_duration_ms=("duration_ms", "mean"),
                explicit_token_nodes=("has_explicit_tokens", "sum"),
                estimated_token_nodes=("has_estimated_tokens", "sum"),
            )
            .sort_values("total_tokens", ascending=False)
        )

    missing_token_nodes = (
        0
        if node_df.empty
        else int(
            (
                (~node_df["has_explicit_tokens"]) & (~node_df["has_estimated_tokens"])
            ).sum()
        )
    )
    return node_df, edge_df, run_df, agent_df, missing_token_nodes


def build_review_summary(
    context_files: list[Path], estimate_from_io: bool = True
) -> tuple[pd.DataFrame, pd.DataFrame, pd.DataFrame, int, int]:
    review_rows: list[dict] = []
    model_rows: list[dict] = []
    agent_rows: list[dict] = []
    missing_token_nodes = 0
    total_runs = 0

    for review_id, context_file in enumerate(context_files, start=1):
        records = parse_agent_context(context_file)
        if not records:
            continue

        node_df, _, run_df, _, missing_nodes = flatten_runs(
            records, estimate_from_io=estimate_from_io
        )
        if node_df.empty:
            continue

        missing_token_nodes += missing_nodes
        total_runs += int(run_df.shape[0])
        review_name = context_file.parent.name
        review_rows.append(
            {
                "review_id": review_id,
                "review_name": review_name,
                "context_path": str(context_file),
                "runs": int(run_df.shape[0]),
                "total_duration_ms": int(run_df["duration_ms"].sum()),
                "input_tokens": int(node_df["input_tokens"].sum()),
                "output_tokens": int(node_df["output_tokens"].sum()),
                "total_tokens": int(node_df["total_tokens"].sum()),
            }
        )

        review_model_df = (
            node_df.assign(model=node_df["model"].replace("", "Unknown"))
            .groupby("model", as_index=False)
            .agg(
                input_tokens=("input_tokens", "sum"),
                output_tokens=("output_tokens", "sum"),
                total_tokens=("total_tokens", "sum"),
            )
        )
        for _, row in review_model_df.iterrows():
            model_rows.append(
                {
                    "review_id": review_id,
                    "review_name": review_name,
                    "model": row["model"],
                    "input_tokens": int(row["input_tokens"]),
                    "output_tokens": int(row["output_tokens"]),
                    "total_tokens": int(row["total_tokens"]),
                }
            )

        review_agent_df = node_df.groupby("agent", as_index=False).agg(
            calls=("node_id", "count")
        )
        for _, row in review_agent_df.iterrows():
            agent_rows.append(
                {
                    "review_id": review_id,
                    "review_name": review_name,
                    "agent": row["agent"],
                    "calls": int(row["calls"]),
                }
            )

    review_df = pd.DataFrame(review_rows)
    review_model_df = pd.DataFrame(model_rows)
    review_agent_df = pd.DataFrame(agent_rows)
    return review_df, review_model_df, review_agent_df, missing_token_nodes, total_runs


def build_model_average(
    review_df: pd.DataFrame, review_model_df: pd.DataFrame
) -> pd.DataFrame:
    if review_df.empty:
        return pd.DataFrame(
            columns=[
                "model",
                "avg_input_tokens",
                "avg_output_tokens",
                "avg_total_tokens",
            ]
        )

    review_count = int(review_df.shape[0])
    if review_model_df.empty:
        return pd.DataFrame(
            columns=[
                "model",
                "avg_input_tokens",
                "avg_output_tokens",
                "avg_total_tokens",
            ]
        )

    model_totals = review_model_df.groupby("model", as_index=False).agg(
        input_tokens=("input_tokens", "sum"),
        output_tokens=("output_tokens", "sum"),
        total_tokens=("total_tokens", "sum"),
        review_coverage=("review_id", "nunique"),
    )
    return (
        model_totals.assign(
            avg_input_tokens=lambda df: df["input_tokens"] / review_count,
            avg_output_tokens=lambda df: df["output_tokens"] / review_count,
            avg_total_tokens=lambda df: df["total_tokens"] / review_count,
        )
        .loc[
            :,
            [
                "model",
                "avg_input_tokens",
                "avg_output_tokens",
                "avg_total_tokens",
                "review_coverage",
            ],
        ]
        .sort_values("avg_total_tokens", ascending=False)
    )


def build_agent_average(
    review_df: pd.DataFrame, review_agent_df: pd.DataFrame
) -> pd.DataFrame:
    if review_df.empty:
        return pd.DataFrame(columns=["agent", "avg_calls"])

    if review_agent_df.empty:
        return pd.DataFrame(columns=["agent", "avg_calls"])

    all_agents = sorted(review_agent_df["agent"].unique())
    all_reviews = review_df[["review_id"]].drop_duplicates()
    complete_index = pd.MultiIndex.from_product(
        [all_reviews["review_id"].tolist(), all_agents], names=["review_id", "agent"]
    )
    calls_df = (
        review_agent_df[["review_id", "agent", "calls"]]
        .set_index(["review_id", "agent"])
        .reindex(complete_index, fill_value=0)
        .reset_index()
    )

    return (
        calls_df.groupby("agent", as_index=False)
        .agg(avg_calls=("calls", "mean"))
        .assign(avg_calls=lambda df: df["avg_calls"].round(2))
        .sort_values("avg_calls", ascending=False)
    )


st.set_page_config(page_title="Agent Context Dashboard", layout="wide")
st.title("Agent Context Dashboard")
st.caption(
    "Aggregate every agent_context.json under a folder and compute per-review averages."
)

with st.sidebar:
    st.header("Data Source")
    context_path = st.text_input(
        "Review folder or agent_context.json path", str(DEFAULT_CONTEXT_DIR)
    )
    estimate_from_io = st.checkbox(
        "Estimate tokens from input/output when missing", value=True
    )

path = Path(context_path)
if not path.exists():
    st.error(f"File not found: {path}")
    st.stop()

context_files = collect_agent_context_files(path)
if not context_files:
    st.warning("No agent_context.json files found under the selected path.")
    st.stop()

review_df, review_model_df, review_agent_df, missing_token_nodes, total_runs = (
    build_review_summary(context_files, estimate_from_io=estimate_from_io)
)

if review_df.empty:
    st.warning("No valid review records found in the selected path.")
    st.stop()

col1, col2, col3, col4 = st.columns(4)
avg_review_duration_ms = float(review_df["total_duration_ms"].mean())
avg_review_input_tokens = float(review_df["input_tokens"].mean())
avg_review_output_tokens = float(review_df["output_tokens"].mean())

col1.metric("Reviews", int(review_df.shape[0]))
col2.metric("Runs", total_runs)
col3.metric("Avg Review Time", f"{avg_review_duration_ms / 1000:,.2f} s")
col4.metric(
    "Avg Review Tokens",
    f"{avg_review_input_tokens:,.0f} / {avg_review_output_tokens:,.0f}",
)

model_avg_df = build_model_average(review_df, review_model_df)
agent_avg_df = build_agent_average(review_df, review_agent_df)

if estimate_from_io:
    st.caption(
        f"Scanned {len(context_files)} files. Missing token nodes after estimation: {missing_token_nodes}."
    )
else:
    st.caption(
        f"Scanned {len(context_files)} files. Missing token nodes: {missing_token_nodes}. "
        "Enable estimation in the sidebar to infer tokens from input/output."
    )

if missing_token_nodes > 0:
    st.info(
        f"{missing_token_nodes} calls do not include token fields and could not be estimated from input/output. "
        "These calls are shown as 0 token cost."
    )

tab_overview, tab_model, tab_agent, tab_raw = st.tabs(
    ["Overview", "Per Model", "Per Agent", "Raw Data"]
)

with tab_overview:
    st.subheader("Average Per Review")
    summary_df = pd.DataFrame(
        [
            {
                "metric": "Total time (ms)",
                "average": round(float(review_df["total_duration_ms"].mean()), 2),
            },
            {
                "metric": "Input tokens",
                "average": round(float(review_df["input_tokens"].mean()), 2),
            },
            {
                "metric": "Output tokens",
                "average": round(float(review_df["output_tokens"].mean()), 2),
            },
            {
                "metric": "Total tokens",
                "average": round(float(review_df["total_tokens"].mean()), 2),
            },
        ]
    )
    st.dataframe(
        summary_df,
        use_container_width=True,
    )

    st.subheader("Per Review Summary")
    st.dataframe(
        review_df.sort_values("review_name").reset_index(drop=True),
        use_container_width=True,
    )

with tab_model:
    st.subheader("Average Tokens Per Review by Model")
    if model_avg_df.empty:
        st.info("No model data found.")
    else:
        st.dataframe(
            model_avg_df.assign(
                avg_input_tokens=lambda df: df["avg_input_tokens"].round(2),
                avg_output_tokens=lambda df: df["avg_output_tokens"].round(2),
                avg_total_tokens=lambda df: df["avg_total_tokens"].round(2),
            ),
            use_container_width=True,
        )

with tab_agent:
    st.subheader("Average Agent Calls Per Review")
    if agent_avg_df.empty:
        st.info("No agent data found.")
    else:
        st.dataframe(agent_avg_df, use_container_width=True)

    st.subheader("Per Review Agent Calls")
    st.dataframe(
        review_agent_df.sort_values(["agent", "review_name"]).reset_index(drop=True),
        use_container_width=True,
    )

with tab_raw:
    st.subheader("Review Data")
    st.dataframe(review_df, use_container_width=True)
    st.subheader("Model Data")
    st.dataframe(review_model_df, use_container_width=True)
    st.subheader("Agent Data")
    st.dataframe(review_agent_df, use_container_width=True)
