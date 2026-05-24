from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)


def test_health_endpoint_responds() -> None:
    resp = client.get("/health")
    assert resp.status_code == 200
    body = resp.json()
    assert body["status"] == "ok"
    assert "db" in body
    assert "redis" in body
