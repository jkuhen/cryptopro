import argparse
import json
from datetime import datetime, timezone
from pathlib import Path

import numpy as np
import pandas as pd
import xgboost as xgb
from sklearn.metrics import log_loss, roc_auc_score
from sklearn.model_selection import train_test_split

FEATURES = [
    "trendScore",
    "rsi",
    "volumeSpike",
    "openInterestChange",
    "fundingRate",
]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Train XGBoost signal scorer model artifact")
    parser.add_argument("--version", default="v1", help="Model version folder name")
    parser.add_argument(
        "--artifact-root",
        default="models/signal_scorer",
        help="Artifact root directory",
    )
    parser.add_argument(
        "--dataset",
        default="",
        help="Optional CSV dataset path. If omitted, synthetic training data is generated.",
    )
    parser.add_argument("--rows", type=int, default=6000, help="Synthetic row count when dataset is omitted")
    parser.add_argument("--seed", type=int, default=42, help="Random seed")
    parser.add_argument("--test-size", type=float, default=0.25, help="Validation split ratio")
    return parser.parse_args()


def generate_synthetic(rows: int, seed: int) -> pd.DataFrame:
    rng = np.random.default_rng(seed)

    trend = rng.uniform(-1.0, 1.0, rows)
    rsi = np.clip(50 + trend * 18 + rng.normal(0.0, 7.5, rows), 5, 95)
    volume_spike = rng.gamma(shape=1.8, scale=0.9, size=rows)
    oi_change = rng.normal(0.0, 2.0, rows)
    funding = rng.normal(0.0, 0.01, rows)

    logits = (
        -0.15
        + 1.2 * trend
        + 0.6 * ((rsi - 50.0) / 50.0)
        + 0.25 * np.clip(volume_spike, 0.0, 4.0)
        + 0.14 * oi_change
        - 4.0 * np.abs(funding)
    )

    probs = 1.0 / (1.0 + np.exp(-logits))
    labels = rng.binomial(1, np.clip(probs, 0.02, 0.98)).astype(np.int32)

    return pd.DataFrame(
        {
            "trendScore": trend,
            "rsi": rsi,
            "volumeSpike": volume_spike,
            "openInterestChange": oi_change,
            "fundingRate": funding,
            "label": labels,
        }
    )


def load_dataset(path: str, rows: int, seed: int) -> pd.DataFrame:
    if path:
        frame = pd.read_csv(path)
    else:
        frame = generate_synthetic(rows=rows, seed=seed)

    missing = [feature for feature in FEATURES + ["label"] if feature not in frame.columns]
    if missing:
        raise ValueError(f"Dataset missing required columns: {missing}")

    frame = frame.copy()
    frame["label"] = frame["label"].astype(int)
    return frame


def train(df: pd.DataFrame, seed: int, test_size: float):
    X = df[FEATURES].values
    y = df["label"].values

    X_train, X_test, y_train, y_test = train_test_split(
        X,
        y,
        test_size=test_size,
        random_state=seed,
        stratify=y,
    )

    model = xgb.XGBClassifier(
        n_estimators=180,
        max_depth=4,
        learning_rate=0.06,
        subsample=0.9,
        colsample_bytree=0.85,
        objective="binary:logistic",
        eval_metric="logloss",
        random_state=seed,
    )
    model.fit(X_train, y_train)

    probs = model.predict_proba(X_test)[:, 1]
    metrics = {
        "rocAuc": float(roc_auc_score(y_test, probs)),
        "logLoss": float(log_loss(y_test, probs)),
        "validationRows": int(len(y_test)),
    }
    return model, metrics


def write_artifact(model: xgb.XGBClassifier, metrics: dict, version: str, artifact_root: str):
    version_dir = Path(artifact_root) / version
    version_dir.mkdir(parents=True, exist_ok=True)

    model_path = version_dir / "model.bin"
    metadata_path = version_dir / "metadata.json"

    model.save_model(model_path)

    metadata = {
        "modelName": "xgboost-artifact",
        "version": version,
        "trainedAtUtc": datetime.now(timezone.utc).isoformat(),
        "featureOrder": FEATURES,
        "objective": "binary:logistic",
        "metrics": metrics,
        "binaryFile": "model.bin",
    }

    metadata_path.write_text(json.dumps(metadata, indent=2), encoding="utf-8")

    return model_path, metadata_path


def main() -> None:
    args = parse_args()
    dataset = load_dataset(path=args.dataset, rows=args.rows, seed=args.seed)
    model, metrics = train(df=dataset, seed=args.seed, test_size=args.test_size)
    model_path, metadata_path = write_artifact(
        model=model,
        metrics=metrics,
        version=args.version,
        artifact_root=args.artifact_root,
    )

    print("Training complete")
    print(f"Model: {model_path}")
    print(f"Metadata: {metadata_path}")
    print(f"Metrics: {json.dumps(metrics)}")


if __name__ == "__main__":
    main()

