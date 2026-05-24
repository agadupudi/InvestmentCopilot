const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://127.0.0.1:8000";

export type Holding = {
  id: number;
  symbol: string;
  quantity: string;
  cost_basis: string;
  notes: string | null;
  created_at: string;
  updated_at: string;
  last_price: string | null;
  market_value: string | null;
  unrealized_pnl: string | null;
  unrealized_pnl_pct: number | null;
};

export type HoldingCreate = {
  symbol: string;
  quantity: string;
  cost_basis: string;
  notes?: string;
};

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...(init?.headers ?? {}),
    },
  });
  if (!res.ok) {
    const text = await res.text().catch(() => "");
    throw new Error(`${res.status} ${res.statusText}: ${text}`);
  }
  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}

export const api = {
  listHoldings: () => request<Holding[]>("/holdings"),
  createHolding: (payload: HoldingCreate) =>
    request<Holding>("/holdings", { method: "POST", body: JSON.stringify(payload) }),
  deleteHolding: (id: number) =>
    request<void>(`/holdings/${id}`, { method: "DELETE" }),
  health: () => request<{ status: string; db: boolean; redis: boolean }>("/health"),
};
