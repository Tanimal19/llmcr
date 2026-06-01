import json
from pathlib import Path
from typing import Any, Dict, List, Optional

import pandas as pd
import plotly.express as px
import streamlit as st

from evaluation_analysis import analysis

try:
    from streamlit.runtime.scriptrunner import get_script_run_ctx
except Exception:  # noqa: BLE001
    get_script_run_ctx = None

DEFAULT_INPUT_DIR = Path(__file__).resolve().parents[1] / "reviews"


def is_streamlit_context() -> bool:
    if get_script_run_ctx is None:
        return False
    return get_script_run_ctx() is not None


def build_summary_df(summary: Dict[str, Dict[str, Any]]) -> pd.DataFrame:
    rows: List[Dict[str, Any]] = []
    for metric, stats in summary.items():
        rows.append(
            {
                "metric": metric,
                "count": stats.get("count"),
                "mean": stats.get("mean"),
                "median": stats.get("median"),
                "min": stats.get("min"),
                "max": stats.get("max"),
                "p25": stats.get("p25"),
                "p75": stats.get("p75"),
            }
        )
    df = pd.DataFrame(rows)
    if df.empty:
        return df
    return df.sort_values("metric").reset_index(drop=True)


def build_per_file_df(per_file: List[Dict[str, Any]]) -> pd.DataFrame:
    rows: List[Dict[str, Any]] = []
    for item in per_file:
        file_path = str(item.get("file") or "")
        raw_metrics = item.get("metrics")
        metrics: Dict[str, Any] = raw_metrics if isinstance(raw_metrics, dict) else {}
        row: Dict[str, Any] = {
            "file": file_path,
            "file_name": Path(file_path).parent.name or Path(file_path).name,
        }
        for metric, value in metrics.items():
            row[metric] = value
        rows.append(row)
    return pd.DataFrame(rows)


def build_long_df(per_file_df: pd.DataFrame, metric_cols: List[str]) -> pd.DataFrame:
    if per_file_df.empty or not metric_cols:
        return pd.DataFrame(columns=["file", "file_name", "metric", "value"])

    long_df = per_file_df.melt(
        id_vars=["file", "file_name"],
        value_vars=metric_cols,
        var_name="metric",
        value_name="value",
    )
    return long_df.dropna(subset=["value"])


def show_summary_charts(summary_df: pd.DataFrame, per_file_df: pd.DataFrame) -> None:
    if summary_df.empty:
        st.warning("No summary data available.")
        return

    std_by_metric: Dict[str, float] = {}
    for metric in summary_df["metric"].tolist():
        if metric in per_file_df.columns and pd.api.types.is_numeric_dtype(
            per_file_df[metric]
        ):
            values = per_file_df[metric].dropna()
            if not values.empty:
                std_by_metric[metric] = float(values.std(ddof=0))

    st.subheader("Metric Means")
    mean_df = summary_df[["metric", "mean", "min", "max"]].copy()
    mean_df["std"] = mean_df["metric"].map(std_by_metric)
    mean_df = mean_df.sort_values("mean", ascending=False).reset_index(drop=True)
    st.dataframe(mean_df, width="stretch")


def show_distribution(per_file_df: pd.DataFrame, metric_cols: List[str]) -> None:
    if per_file_df.empty or not metric_cols:
        st.warning("No per-file metric data available.")
        return

    st.subheader("Metric Scatter")
    ordered_metrics = sorted(metric_cols)
    x_col, y_col = st.columns(2)
    x_metric = x_col.selectbox("X-axis metric", options=ordered_metrics, index=0)
    y_default = 1 if len(ordered_metrics) > 1 else 0
    y_metric = y_col.selectbox(
        "Y-axis metric", options=ordered_metrics, index=y_default
    )

    scatter_df = per_file_df.dropna(subset=[x_metric, y_metric]).copy()

    if scatter_df.empty:
        st.warning("No data available for selected metric pair.")
        return

    scatter_fig = px.scatter(
        scatter_df,
        x=x_metric,
        y=y_metric,
        text="file_name",
        hover_data=["file"],
        title=f"{x_metric} vs {y_metric}",
    )
    scatter_fig.update_traces(textposition="top center")
    scatter_fig.update_layout(xaxis_title=x_metric, yaxis_title=y_metric)
    st.plotly_chart(scatter_fig, width="stretch")


def load_report_ui() -> Optional[Dict[str, Any]]:
    st.sidebar.header("Data Source")

    path_text = st.sidebar.text_input("Folder Path", str(DEFAULT_INPUT_DIR))
    path = Path(path_text).expanduser()
    try:
        return analysis(path)
    except Exception as exc:  # noqa: BLE001
        st.error(f"Failed to analyze folder: {exc}")
        return None


def main() -> None:
    st.set_page_config(page_title="Review Evaluation Dashboard", layout="wide")
    st.title("Review Evaluation Dashboard")

    report = load_report_ui()
    if report is None:
        return

    raw_summary = report.get("summary")
    summary: Dict[str, Dict[str, Any]] = (
        raw_summary if isinstance(raw_summary, dict) else {}
    )

    raw_per_file = report.get("per_file")
    per_file: List[Dict[str, Any]] = []
    if isinstance(raw_per_file, list):
        per_file = [item for item in raw_per_file if isinstance(item, dict)]

    summary_df = build_summary_df(summary)
    per_file_df = build_per_file_df(per_file)

    metric_cols = [
        col
        for col in per_file_df.columns
        if col not in {"file", "file_name"}
        and pd.api.types.is_numeric_dtype(per_file_df[col])
    ]
    if not metric_cols:
        return

    show_summary_charts(summary_df, per_file_df)
    show_distribution(per_file_df, metric_cols)


if __name__ == "__main__":
    if not is_streamlit_context():
        print("This script is a Streamlit app.")
        print("Run it with: streamlit run evaluation/post_eval/evaluation_dashboard.py")
        raise SystemExit(0)
    main()
