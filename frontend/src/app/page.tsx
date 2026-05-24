"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { api, Holding, HoldingCreate } from "@/lib/api";

const fmt = (v: string | number | null | undefined, digits = 2) => {
  if (v === null || v === undefined || v === "") return "—";
  const n = typeof v === "string" ? Number(v) : v;
  if (Number.isNaN(n)) return "—";
  return n.toLocaleString(undefined, {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
  });
};

export default function HomePage() {
  const qc = useQueryClient();

  const holdingsQuery = useQuery({
    queryKey: ["holdings"],
    queryFn: api.listHoldings,
    refetchInterval: 60_000,
  });

  const createMut = useMutation({
    mutationFn: (payload: HoldingCreate) => api.createHolding(payload),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["holdings"] }),
  });

  const deleteMut = useMutation({
    mutationFn: (id: number) => api.deleteHolding(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["holdings"] }),
  });

  const [form, setForm] = useState({ symbol: "", quantity: "", cost_basis: "", notes: "" });

  const totals = (holdingsQuery.data ?? []).reduce(
    (acc, h) => {
      const mv = Number(h.market_value ?? 0);
      const cost = Number(h.cost_basis) * Number(h.quantity);
      const pnl = Number(h.unrealized_pnl ?? 0);
      acc.marketValue += mv;
      acc.cost += cost;
      acc.pnl += pnl;
      return acc;
    },
    { marketValue: 0, cost: 0, pnl: 0 },
  );

  return (
    <main className="mx-auto w-full max-w-5xl p-6 sm:p-10 space-y-8">
      <header className="flex items-baseline justify-between">
        <h1 className="text-2xl sm:text-3xl font-bold">Investment Co-Pilot</h1>
        <span className="text-xs uppercase tracking-wider text-neutral-500">
          Phase 1 · MVP
        </span>
      </header>

      <section className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <Card label="Market Value" value={`$${fmt(totals.marketValue)}`} />
        <Card label="Cost Basis" value={`$${fmt(totals.cost)}`} />
        <Card
          label="Unrealized P&L"
          value={`${totals.pnl >= 0 ? "+" : "-"}$${fmt(Math.abs(totals.pnl))}`}
          tone={totals.pnl >= 0 ? "positive" : "negative"}
        />
      </section>

      <section className="rounded-lg border border-neutral-200 dark:border-neutral-800 bg-white dark:bg-neutral-900 p-4 sm:p-6">
        <h2 className="text-lg font-semibold mb-4">Add holding</h2>
        <form
          className="grid grid-cols-1 sm:grid-cols-5 gap-3"
          onSubmit={(e) => {
            e.preventDefault();
            if (!form.symbol || !form.quantity || !form.cost_basis) return;
            createMut.mutate(
              {
                symbol: form.symbol,
                quantity: form.quantity,
                cost_basis: form.cost_basis,
                notes: form.notes || undefined,
              },
              {
                onSuccess: () =>
                  setForm({ symbol: "", quantity: "", cost_basis: "", notes: "" }),
              },
            );
          }}
        >
          <Input
            placeholder="Symbol (e.g. AAPL)"
            value={form.symbol}
            onChange={(v) => setForm((f) => ({ ...f, symbol: v.toUpperCase() }))}
          />
          <Input
            placeholder="Quantity"
            inputMode="decimal"
            value={form.quantity}
            onChange={(v) => setForm((f) => ({ ...f, quantity: v }))}
          />
          <Input
            placeholder="Avg cost"
            inputMode="decimal"
            value={form.cost_basis}
            onChange={(v) => setForm((f) => ({ ...f, cost_basis: v }))}
          />
          <Input
            placeholder="Notes (optional)"
            value={form.notes}
            onChange={(v) => setForm((f) => ({ ...f, notes: v }))}
          />
          <button
            type="submit"
            disabled={createMut.isPending}
            className="rounded-md bg-emerald-600 hover:bg-emerald-500 text-white font-medium px-3 py-2 disabled:opacity-50"
          >
            {createMut.isPending ? "Adding…" : "Add"}
          </button>
        </form>
        {createMut.error && (
          <p className="mt-3 text-sm text-red-600">{(createMut.error as Error).message}</p>
        )}
      </section>

      <section className="rounded-lg border border-neutral-200 dark:border-neutral-800 bg-white dark:bg-neutral-900">
        <h2 className="text-lg font-semibold p-4 sm:p-6 pb-3">Holdings</h2>
        <div className="overflow-x-auto">
          <table className="min-w-full text-sm">
            <thead className="text-left text-neutral-500 border-y border-neutral-200 dark:border-neutral-800">
              <tr>
                <Th>Symbol</Th>
                <Th right>Qty</Th>
                <Th right>Avg Cost</Th>
                <Th right>Last</Th>
                <Th right>Market Value</Th>
                <Th right>P&L</Th>
                <Th right>%</Th>
                <Th />
              </tr>
            </thead>
            <tbody>
              {holdingsQuery.isLoading ? (
                <tr>
                  <td colSpan={8} className="p-6 text-center text-neutral-500">
                    Loading…
                  </td>
                </tr>
              ) : holdingsQuery.data && holdingsQuery.data.length > 0 ? (
                holdingsQuery.data.map((h: Holding) => (
                  <tr
                    key={h.id}
                    className="border-b border-neutral-100 dark:border-neutral-800"
                  >
                    <Td className="font-medium">{h.symbol}</Td>
                    <Td right>{fmt(h.quantity, 4)}</Td>
                    <Td right>${fmt(h.cost_basis)}</Td>
                    <Td right>{h.last_price ? `$${fmt(h.last_price)}` : "—"}</Td>
                    <Td right>{h.market_value ? `$${fmt(h.market_value)}` : "—"}</Td>
                    <Td
                      right
                      className={
                        Number(h.unrealized_pnl ?? 0) >= 0 ? "text-emerald-600" : "text-red-600"
                      }
                    >
                      {h.unrealized_pnl
                        ? `${Number(h.unrealized_pnl) >= 0 ? "+" : "-"}$${fmt(Math.abs(Number(h.unrealized_pnl)))}`
                        : "—"}
                    </Td>
                    <Td
                      right
                      className={
                        (h.unrealized_pnl_pct ?? 0) >= 0 ? "text-emerald-600" : "text-red-600"
                      }
                    >
                      {h.unrealized_pnl_pct !== null
                        ? `${h.unrealized_pnl_pct >= 0 ? "+" : ""}${h.unrealized_pnl_pct.toFixed(2)}%`
                        : "—"}
                    </Td>
                    <Td right>
                      <button
                        onClick={() => deleteMut.mutate(h.id)}
                        className="text-neutral-500 hover:text-red-600"
                        aria-label={`Delete ${h.symbol}`}
                      >
                        ✕
                      </button>
                    </Td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan={8} className="p-6 text-center text-neutral-500">
                    No holdings yet. Add one above.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </section>

      <footer className="text-xs text-neutral-500 text-center">
        For personal use. Not financial advice.
      </footer>
    </main>
  );
}

function Card({
  label,
  value,
  tone,
}: {
  label: string;
  value: string;
  tone?: "positive" | "negative";
}) {
  const toneCls =
    tone === "positive"
      ? "text-emerald-600"
      : tone === "negative"
        ? "text-red-600"
        : "";
  return (
    <div className="rounded-lg border border-neutral-200 dark:border-neutral-800 bg-white dark:bg-neutral-900 p-4">
      <div className="text-xs uppercase tracking-wider text-neutral-500">{label}</div>
      <div className={`text-2xl font-semibold mt-1 ${toneCls}`}>{value}</div>
    </div>
  );
}

function Input({
  value,
  onChange,
  ...rest
}: { value: string; onChange: (v: string) => void } & Omit<
  React.InputHTMLAttributes<HTMLInputElement>,
  "value" | "onChange"
>) {
  return (
    <input
      {...rest}
      value={value}
      onChange={(e) => onChange(e.target.value)}
      className="rounded-md border border-neutral-300 dark:border-neutral-700 bg-white dark:bg-neutral-950 px-3 py-2 text-sm"
    />
  );
}

function Th({ children, right }: { children?: React.ReactNode; right?: boolean }) {
  return (
    <th
      className={`px-4 py-2 text-xs font-medium uppercase tracking-wider ${right ? "text-right" : ""}`}
    >
      {children}
    </th>
  );
}

function Td({
  children,
  right,
  className = "",
}: {
  children?: React.ReactNode;
  right?: boolean;
  className?: string;
}) {
  return (
    <td className={`px-4 py-3 ${right ? "text-right" : ""} ${className}`}>{children}</td>
  );
}
