import json
from pathlib import Path

import pandas as pd
import plotly.express as px
import plotly.graph_objects as go
import streamlit as st

DEFAULT_CONTEXT = (
    Path(__file__).parent / "data" / "reviews" / "llmcr" / "5091" / "agent_context.json"
)


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


def extract_token_counts(node: dict) -> tuple[int, int, bool]:
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
            input_tokens = total_tokens

    has_explicit_tokens = input_tokens > 0 or output_tokens > 0
    return input_tokens, output_tokens, has_explicit_tokens


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


def flatten_runs(
    records: list[dict],
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

        input_tokens, output_tokens, has_explicit_tokens = extract_token_counts(node)

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
            )
            .sort_values("total_tokens", ascending=False)
        )

    missing_token_nodes = (
        0 if node_df.empty else int((~node_df["has_explicit_tokens"]).sum())
    )
    return node_df, edge_df, run_df, agent_df, missing_token_nodes


def make_sankey(edge_df: pd.DataFrame, title: str) -> go.Figure | None:
    if edge_df.empty:
        return None

    flow = (
        edge_df.groupby(["source_agent", "target_agent"], as_index=False)
        .size()
        .rename(columns={"size": "count"})
        .sort_values("count", ascending=False)
    )

    nodes = sorted(set(flow["source_agent"]).union(set(flow["target_agent"])))
    idx = {name: i for i, name in enumerate(nodes)}

    fig = go.Figure(
        data=[
            go.Sankey(
                node=dict(label=nodes, pad=20, thickness=18),
                link=dict(
                    source=[idx[x] for x in flow["source_agent"]],
                    target=[idx[x] for x in flow["target_agent"]],
                    value=flow["count"].tolist(),
                    customdata=list(
                        zip(flow["source_agent"], flow["target_agent"], flow["count"])
                    ),
                    hovertemplate="%{customdata[0]} -> %{customdata[1]}<br>Calls: %{value}<extra></extra>",
                ),
            )
        ]
    )
    fig.update_layout(title=title, height=520)
    return fig


st.set_page_config(page_title="Agent Context Dashboard", layout="wide")
st.title("Agent Context Dashboard")
st.caption("Visualize agent call diagram and input/output token cost.")

with st.sidebar:
    st.header("Data Source")
    context_path = st.text_input("agent_context.json path", str(DEFAULT_CONTEXT))

path = Path(context_path)
if not path.exists():
    st.error(f"File not found: {path}")
    st.stop()

records = parse_agent_context(path)
if not records:
    st.warning("No valid records found in the selected file.")
    st.stop()

node_df, edge_df, run_df, agent_df, missing_token_nodes = flatten_runs(records)

if node_df.empty:
    st.warning("No agent nodes found.")
    st.stop()

col1, col2, col3, col4 = st.columns(4)
col1.metric("Runs", int(run_df.shape[0]))
col2.metric("Agent Calls", int(node_df.shape[0]))
col3.metric("Input Tokens", f"{int(node_df['input_tokens'].sum()):,}")
col4.metric("Output Tokens", f"{int(node_df['output_tokens'].sum()):,}")

if missing_token_nodes > 0:
    st.info(
        f"{missing_token_nodes} calls do not include explicit token fields in the context. "
        "These calls are shown as 0 token cost."
    )

tab_overview, tab_run, tab_agent, tab_raw = st.tabs(
    ["Overview", "Per Run Diagram", "Per Agent", "Raw Data"]
)

with tab_overview:
    st.subheader("Global Call Diagram")
    global_fig = make_sankey(edge_df, "All Agent Transitions")
    if global_fig is None:
        st.info("No transitions found. The data may contain only single-level calls.")
    else:
        st.plotly_chart(global_fig, use_container_width=True)

    st.subheader("Token Cost by Agent")
    token_long = agent_df.melt(
        id_vars=["agent"],
        value_vars=["input_tokens", "output_tokens"],
        var_name="token_type",
        value_name="tokens",
    )
    token_long["token_type"] = token_long["token_type"].map(
        {"input_tokens": "Input", "output_tokens": "Output"}
    )
    token_fig = px.bar(
        token_long,
        x="agent",
        y="tokens",
        color="token_type",
        barmode="stack",
        title="Input/Output Token Cost per Agent",
        labels={"agent": "Agent", "tokens": "Tokens", "token_type": "Type"},
    )
    token_fig.update_layout(xaxis_tickangle=-25, height=430)
    st.plotly_chart(token_fig, use_container_width=True)

with tab_run:
    st.subheader("Call Diagram for a Specific Run")
    run_labels = {
        int(row["run_id"]): f"Run {int(row['run_id'])}: {row['root_agent']}"
        for _, row in run_df.iterrows()
    }
    selected_run = st.selectbox(
        "Run",
        options=run_df["run_id"].tolist(),
        format_func=lambda rid: run_labels.get(int(rid), f"Run {rid}"),
    )

    run_edges = edge_df[edge_df["run_id"] == selected_run].copy()
    run_nodes = node_df[node_df["run_id"] == selected_run].copy()
    run_meta = run_df[run_df["run_id"] == selected_run].iloc[0]

    c1, c2, c3 = st.columns(3)
    c1.metric("Run Input Tokens", f"{int(run_meta['input_tokens']):,}")
    c2.metric("Run Output Tokens", f"{int(run_meta['output_tokens']):,}")
    c3.metric("Run Calls", int(run_meta["nodes"]))

    run_fig = make_sankey(run_edges, f"Run {selected_run} - Agent Transitions")
    if run_fig is None:
        st.info("No child transitions found in this run.")
    else:
        st.plotly_chart(run_fig, use_container_width=True)

    st.dataframe(
        run_nodes[
            [
                "node_id",
                "parent_node_id",
                "depth",
                "agent",
                "model",
                "duration_ms",
                "input_tokens",
                "output_tokens",
                "total_tokens",
            ]
        ]
        .sort_values(["depth", "node_id"])
        .reset_index(drop=True),
        use_container_width=True,
    )

with tab_agent:
    st.subheader("Per-Agent Summary")
    st.dataframe(
        agent_df[
            [
                "agent",
                "calls",
                "input_tokens",
                "output_tokens",
                "total_tokens",
                "avg_duration_ms",
                "explicit_token_nodes",
            ]
        ].assign(avg_duration_ms=lambda x: x["avg_duration_ms"].round(1)),
        use_container_width=True,
    )

    selected_agent = st.selectbox("Agent", options=sorted(agent_df["agent"].unique()))
    agent_edges = edge_df[
        (edge_df["source_agent"] == selected_agent)
        | (edge_df["target_agent"] == selected_agent)
    ].copy()
    agent_fig = make_sankey(agent_edges, f"Transitions Related to {selected_agent}")
    if agent_fig is None:
        st.info("No transitions found for this agent.")
    else:
        st.plotly_chart(agent_fig, use_container_width=True)

with tab_raw:
    st.subheader("Run Data")
    st.dataframe(run_df, use_container_width=True)
    st.subheader("Node Data")
    st.dataframe(node_df, use_container_width=True)
    st.subheader("Edge Data")
    st.dataframe(edge_df, use_container_width=True)
