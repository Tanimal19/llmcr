import json
from pathlib import Path

import pandas as pd
import plotly.express as px
import streamlit as st

# ── page config ──────────────────────────────────────────────────────────────
st.set_page_config(page_title="Evaluation Dashboard", layout="wide")
st.title("📊 Evaluation Results Dashboard")

# ── helpers ──────────────────────────────────────────────────────────────────
DEFAULT_DIR = Path(__file__).parent / "data" / "reviews"

METRIC_LABELS = {
    "GroundingEvaluator/hallucination_rate": "Hallucination Rate ↓",
    "GroundingEvaluator/coverage_score": "Coverage Score ↑",
    "GroundingEvaluator/mentioned_entities": "Mentioned Entities",
    "GroundingEvaluator/pr_entities": "PR Entities",
    "GroundingEvaluator/mentioned_pr_entities": "Mentioned PR Entities",
    "AlignmentEvaluator/comment_precision": "Comment Precision ↑",
    "AlignmentEvaluator/comment_recall": "Comment Recall ↑",
    "AlignmentEvaluator/comment_f1": "Comment F1 ↑",
    "AlignmentEvaluator/interpretation_precision": "Interp. Precision ↑",
    "AlignmentEvaluator/interpretation_recall": "Interp. Recall ↑",
    "AlignmentEvaluator/interpretation_f1": "Interp. F1 ↑",
    "RepetitiveEvaluator": "Repetitive Score ↓",
}


def flatten_results(record: dict) -> dict:
    """Flatten nested results dict into dot-separated metric keys."""
    row = {"pr_id": record["pr_id"], "group": record["group"]}
    results = record.get("results", {})
    for evaluator, value in results.items():
        if isinstance(value, dict):
            for metric, v in value.items():
                if isinstance(v, (int, float)):
                    row[f"{evaluator}/{metric}"] = v
        elif isinstance(value, (int, float)):
            row[evaluator] = value
    return row


def load_jsonl(path: Path) -> list[dict]:
    rows = []
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line:
                rows.append(flatten_results(json.loads(line)))
    return rows


# ── sidebar: file selection ───────────────────────────────────────────────────
st.sidebar.header("Data Sources")

all_rows: list[dict] = []

jsonl_files = sorted(DEFAULT_DIR.rglob("evaluation_results.jsonl"))
selected = st.sidebar.multiselect(
    "Select JSONL files",
    options=[str(p) for p in jsonl_files],
    default=[str(p) for p in jsonl_files],
    format_func=lambda p: Path(p).parent.name,
)
for path_str in selected:
    all_rows.extend(load_jsonl(Path(path_str)))

if not all_rows:
    st.info("No data loaded. Select files in the sidebar.")
    st.stop()

df = pd.DataFrame(all_rows)

# ── derive available metrics ──────────────────────────────────────────────────
numeric_cols = [
    c
    for c in df.columns
    if c not in ("pr_id", "group") and pd.api.types.is_numeric_dtype(df[c])
]
metric_display = {c: METRIC_LABELS.get(c, c) for c in numeric_cols}

# ── filters ───────────────────────────────────────────────────────────────────
st.sidebar.header("Filters")
all_groups = sorted(df["group"].unique())
selected_groups = st.sidebar.multiselect("Groups", all_groups, default=all_groups)
all_prs = sorted(df["pr_id"].unique())
selected_prs = st.sidebar.multiselect("PR IDs", all_prs, default=all_prs)

df_filtered = df[
    df["group"].isin(selected_groups) & df["pr_id"].isin(selected_prs)
].copy()

if df_filtered.empty:
    st.warning("No records match the current filter.")
    st.stop()

# ── tabs ──────────────────────────────────────────────────────────────────────
tab_avg, tab_pr, tab_raw = st.tabs(["Group Averages", "Per-PR Comparison", "Raw Data"])

# ─────────────────────────────────────────────────────────────────────────────
# TAB 1 – GROUP AVERAGES
# ─────────────────────────────────────────────────────────────────────────────
with tab_avg:
    st.subheader("Average metrics per group")

    avg_df = (
        df_filtered.groupby("group")[numeric_cols]
        .mean()
        .reset_index()
        .rename(columns=metric_display)
    )

    # Metric selector
    selected_avg_metrics = st.multiselect(
        "Metrics to display",
        options=list(metric_display.values()),
        default=list(metric_display.values())[:6],
        key="avg_metrics",
    )

    if selected_avg_metrics:
        plot_df = avg_df.melt(
            id_vars="group",
            value_vars=selected_avg_metrics,
            var_name="Metric",
            value_name="Value",
        )
        fig = px.bar(
            plot_df,
            x="Metric",
            y="Value",
            color="group",
            barmode="group",
            height=450,
            title="Average Metric Values by Group",
        )
        fig.update_layout(xaxis_tickangle=-30)
        st.plotly_chart(fig, use_container_width=True)

    st.dataframe(
        avg_df.set_index("group")
        .rename(columns={v: v for v in metric_display.values()})
        .round(4),
        use_container_width=True,
    )

# ─────────────────────────────────────────────────────────────────────────────
# TAB 2 – PER-PR COMPARISON
# ─────────────────────────────────────────────────────────────────────────────
with tab_pr:
    st.subheader("Per-PR metric comparison across groups")

    col1, col2 = st.columns(2)
    chosen_metric_label = col1.selectbox(
        "Metric",
        options=list(metric_display.values()),
        key="pr_metric",
    )
    chosen_metric = next(
        k for k, v in metric_display.items() if v == chosen_metric_label
    )

    if chosen_metric in df_filtered.columns:
        pr_df = df_filtered[["pr_id", "group", chosen_metric]].dropna()
        fig2 = px.bar(
            pr_df,
            x="pr_id",
            y=chosen_metric,
            color="group",
            barmode="group",
            labels={"pr_id": "PR ID", chosen_metric: chosen_metric_label},
            title=f"{chosen_metric_label} per PR",
            height=450,
        )
        fig2.update_xaxes(type="category")
        st.plotly_chart(fig2, use_container_width=True)

        # Pivot table
        pivot = pr_df.pivot_table(
            index="pr_id", columns="group", values=chosen_metric
        ).round(4)
        st.dataframe(pivot, use_container_width=True)
    else:
        st.info(f"Metric `{chosen_metric}` not available in the loaded data.")

    st.divider()
    st.subheader("All metrics for a single PR")
    chosen_pr = st.selectbox(
        "PR ID", sorted(df_filtered["pr_id"].unique()), key="single_pr"
    )
    pr_single = (
        df_filtered[df_filtered["pr_id"] == chosen_pr][["group"] + numeric_cols]
        .set_index("group")
        .round(4)
    )
    pr_single.columns = [metric_display.get(c, c) for c in pr_single.columns]
    st.dataframe(pr_single, use_container_width=True)

    # Radar / line chart for single PR
    radar_metrics = [
        metric_display[c] for c in numeric_cols if c in df_filtered.columns
    ]
    radar_df = pr_single.reset_index().melt(
        id_vars="group", var_name="Metric", value_name="Value"
    )
    fig3 = px.line_polar(
        radar_df,
        r="Value",
        theta="Metric",
        color="group",
        line_close=True,
        title=f"Radar Chart – PR {chosen_pr}",
    )
    st.plotly_chart(fig3, use_container_width=True)

# ─────────────────────────────────────────────────────────────────────────────
# TAB 3 – RAW DATA
# ─────────────────────────────────────────────────────────────────────────────
with tab_raw:
    st.subheader("Raw filtered data")
    display_df = df_filtered.copy()
    display_df.columns = [metric_display.get(c, c) for c in display_df.columns]
    st.dataframe(
        display_df.sort_values(["pr_id", "group"]).reset_index(drop=True),
        use_container_width=True,
    )
    csv = display_df.to_csv(index=False).encode("utf-8")
    st.download_button("Download CSV", csv, "evaluation_results.csv", "text/csv")
