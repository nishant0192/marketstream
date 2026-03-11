"use client";

import { useEffect, useRef, useState } from "react";
import {
  AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
} from "recharts";

interface SymbolLatency {
  symbol: string;
  eventType: string;
  lastPrice: string;
  eventTimestamp: string;
  ageMs: number;
  syncStatus: "FRESH" | "OK" | "STALE";
}
interface PipelineMetrics {
  checkedAt: string;
  status: "IN_SYNC" | "LAGGING" | "STALE" | "NO_DATA";
  cachedSymbols: number;
  minLatencyMs: number;
  maxLatencyMs: number;
  avgLatencyMs: number;
  symbols: SymbolLatency[];
}
interface CacheMetrics {
  cacheHits: number;
  cacheMisses: number;
  totalRequests: number;
  hitRatio: number;
  cachedSymbols: number;
}
interface LogEntry { time: string; status: string; avgMs: number; }
interface ChartPoint { t: string; avg: number; min: number; max: number; }

const API = "http://localhost:8080";
const POLL_MS = 2000;
const MAX_LOG = 80;
const MAX_CHART = 40;

const statusColor = (s: string) =>
  s === "IN_SYNC" || s === "FRESH" ? "#10b981" : s === "LAGGING" || s === "OK" ? "#f59e0b" : "#ef4444";
const typeColor = (t: string) =>
  t === "STOCK" ? "#3b82f6" : t === "CRYPTO" ? "#8b5cf6" : "#06b6d4";
const fmtPrice = (p: string) =>
  parseFloat(p).toLocaleString("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 6 });
const fmtTime = (iso: string) =>
  new Date(iso).toLocaleTimeString("en-US", { hour12: false });

export default function Dashboard() {
  const [pipeline, setPipeline] = useState<PipelineMetrics | null>(null);
  const [cache, setCache] = useState<CacheMetrics | null>(null);
  const [logs, setLogs] = useState<LogEntry[]>([]);
  const [chart, setChart] = useState<ChartPoint[]>([]);
  const [sessionMin, setSessionMin] = useState<number>(Infinity);
  const [sessionMax, setSessionMax] = useState<number>(0);
  const [sessionSum, setSessionSum] = useState(0);
  const [pollCount, setPollCount] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [connected, setConnected] = useState(false);
  const [lastUpdate, setLastUpdate] = useState<Date | null>(null);
  const [filter, setFilter] = useState<"ALL" | "STOCK" | "FOREX" | "CRYPTO">("ALL");
  const timer = useRef<ReturnType<typeof setTimeout>>(null);

  const fetchData = async () => {
    try {
      const [pRes, cRes] = await Promise.all([
        fetch(`${API}/api/metrics/pipeline`, { cache: "no-store" }),
        fetch(`${API}/api/metrics/cache`, { cache: "no-store" }),
      ]);
      if (!pRes.ok) throw new Error(`HTTP ${pRes.status}`);
      const p: PipelineMetrics = await pRes.json();
      const c: CacheMetrics = await cRes.json();

      setPipeline(p);
      setCache(c);
      setConnected(true);
      setError(null);
      setLastUpdate(new Date());

      const now = new Date().toLocaleTimeString("en-US", { hour12: false });
      setChart(h => [...h.slice(-(MAX_CHART - 1)), { t: now, avg: p.avgLatencyMs, min: p.minLatencyMs, max: p.maxLatencyMs }]);
      setSessionMin(prev => Math.min(prev === Infinity ? p.minLatencyMs : prev, p.minLatencyMs));
      setSessionMax(prev => Math.max(prev, p.maxLatencyMs));
      setSessionSum(prev => prev + p.avgLatencyMs);
      setPollCount(prev => prev + 1);
      setLogs(prev => [{ time: now, status: p.status, avgMs: p.avgLatencyMs }, ...prev].slice(0, MAX_LOG));
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : String(e));
      setConnected(false);
    } finally {
      timer.current = setTimeout(fetchData, POLL_MS);
    }
  };

  useEffect(() => {
    fetchData();
    return () => { if (timer.current) clearTimeout(timer.current); };
  }, []);

  const sessionAvg = pollCount > 0 ? Math.round(sessionSum / pollCount) : 0;
  const filtered = pipeline?.symbols.filter(s => filter === "ALL" || s.eventType === filter) ?? [];

  return (
    <div style={{ minHeight: "100vh", background: "#090d17", color: "#e2e8f0", fontFamily: "'Inter', system-ui, sans-serif" }}>

      {/* Header */}
      <header style={{ borderBottom: "1px solid #1a2640", padding: "12px 24px", display: "flex", alignItems: "center", gap: 14, position: "sticky", top: 0, background: "#090d17ee", backdropFilter: "blur(10px)", zIndex: 100 }}>
        <div style={{ width: 32, height: 32, borderRadius: 7, background: "linear-gradient(135deg,#3b82f6,#8b5cf6)", display: "flex", alignItems: "center", justifyContent: "center", fontWeight: 800, fontSize: 12, color: "#fff" }}>MS</div>
        <div>
          <span style={{ fontWeight: 700, fontSize: 15 }}>MarketStream</span>
          <span style={{ color: "#475569", fontSize: 12, marginLeft: 8 }}>Pipeline Monitor</span>
        </div>
        <div style={{ marginLeft: "auto", display: "flex", alignItems: "center", gap: 16 }}>
          {pipeline && (
            <span style={{ display: "flex", alignItems: "center", gap: 6, padding: "4px 12px", borderRadius: 999, background: `${statusColor(pipeline.status)}18`, border: `1px solid ${statusColor(pipeline.status)}44`, color: statusColor(pipeline.status), fontWeight: 600, fontSize: 12 }}>
              <span style={{ width: 6, height: 6, borderRadius: "50%", background: statusColor(pipeline.status), display: "inline-block", animation: "pulse-dot 2s ease-in-out infinite" }} />
              {pipeline.status.replace("_", " ")}
            </span>
          )}
          <span style={{ fontSize: 11, color: connected ? "#475569" : "#ef4444" }}>
            {connected ? `live · ${lastUpdate ? Math.floor((Date.now() - lastUpdate.getTime()) / 1000) : 0}s ago` : "disconnected"}
          </span>
        </div>
      </header>

      <main style={{ padding: "20px 24px", maxWidth: 1400, margin: "0 auto" }}>

        {error && (
          <div style={{ background: "#ef444418", border: "1px solid #ef444440", borderRadius: 8, padding: "10px 14px", marginBottom: 16, color: "#ef4444", fontSize: 13 }}>
            ⚠ Cannot reach <code style={{ fontFamily: "monospace" }}>localhost:8080</code> — {error}
          </div>
        )}

        {/* Summary row — 2 cards */}
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12, marginBottom: 14 }}>

          {/* Latency card */}
          <div style={{ background: "#0e1526", border: "1px solid #1a2640", borderRadius: 12, padding: "18px 22px" }}>
            <div style={{ fontSize: 11, color: "#475569", textTransform: "uppercase", letterSpacing: "0.07em", marginBottom: 10 }}>End-to-End Latency · Producer → Redis</div>
            <div style={{ display: "flex", alignItems: "flex-end", gap: 24, marginBottom: 14 }}>
              <div>
                <div style={{ fontSize: 11, color: "#334155", marginBottom: 2 }}>Current avg</div>
                <div style={{ fontSize: 46, fontWeight: 800, color: "#10b981", fontFamily: "monospace", lineHeight: 1 }}>
                  {pipeline?.avgLatencyMs ?? "—"}<span style={{ fontSize: 18, fontWeight: 400, color: "#475569" }}>ms</span>
                </div>
              </div>
              <div style={{ display: "flex", gap: 20, paddingBottom: 6 }}>
                <Stat label="Session min" value={sessionMin === Infinity ? "—" : `${sessionMin}ms`} color="#3b82f6" />
                <Stat label="Session avg" value={`${sessionAvg}ms`} color="#8b5cf6" />
                <Stat label="Session max" value={`${sessionMax}ms`} color="#f59e0b" />
                <Stat label="Polls" value={String(pollCount)} color="#475569" />
              </div>
            </div>
            {/* Range bar */}
            <div style={{ display: "flex", flexDirection: "column", gap: 4 }}>
              <div style={{ height: 5, background: "#1a2640", borderRadius: 3, overflow: "hidden", position: "relative" }}>
                <div style={{ position: "absolute", left: `${sessionMax > 0 && sessionMin !== Infinity ? (sessionMin / sessionMax) * 100 : 0}%`, right: `${pipeline && sessionMax > 0 ? 100 - (pipeline.avgLatencyMs / sessionMax) * 100 : 100}%`, height: "100%", background: "linear-gradient(90deg,#3b82f6,#10b981)", borderRadius: 3 }} />
                {pipeline && sessionMax > 0 && (
                  <div style={{ position: "absolute", left: `${Math.min(97, (pipeline.avgLatencyMs / sessionMax) * 100)}%`, top: -1, width: 3, height: 7, background: "#fff", borderRadius: 2, opacity: 0.8 }} />
                )}
              </div>
              <div style={{ fontSize: 10, color: "#1e3a5f" }}>Snapshot: {pipeline?.minLatencyMs}ms — {pipeline?.maxLatencyMs}ms</div>
            </div>
          </div>

          {/* Cache card */}
          <div style={{ background: "#0e1526", border: "1px solid #1a2640", borderRadius: 12, padding: "18px 22px" }}>
            <div style={{ fontSize: 11, color: "#475569", textTransform: "uppercase", letterSpacing: "0.07em", marginBottom: 10 }}>Cache Performance · Redis</div>
            <div style={{ display: "flex", alignItems: "flex-end", gap: 24, marginBottom: 14 }}>
              <div>
                <div style={{ fontSize: 11, color: "#334155", marginBottom: 2 }}>Hit ratio</div>
                <div style={{ fontSize: 46, fontWeight: 800, color: "#06b6d4", fontFamily: "monospace", lineHeight: 1 }}>
                  {cache ? `${(cache.hitRatio * 100).toFixed(0)}` : "—"}<span style={{ fontSize: 18, fontWeight: 400, color: "#475569" }}>%</span>
                </div>
              </div>
              <div style={{ display: "flex", gap: 20, paddingBottom: 6 }}>
                <Stat label="Hits" value={cache?.cacheHits.toLocaleString() ?? "0"} color="#10b981" />
                <Stat label="Misses" value={cache?.cacheMisses.toLocaleString() ?? "0"} color="#f59e0b" />
                <Stat label="Total" value={cache?.totalRequests.toLocaleString() ?? "0"} color="#64748b" />
                <Stat label="Symbols" value={String(pipeline?.cachedSymbols ?? 0)} color="#8b5cf6" />
              </div>
            </div>
            <div style={{ display: "flex", flexDirection: "column", gap: 4 }}>
              <div style={{ height: 5, background: "#1a2640", borderRadius: 3, overflow: "hidden" }}>
                <div style={{ width: `${cache ? cache.hitRatio * 100 : 0}%`, height: "100%", background: "linear-gradient(90deg,#10b981,#06b6d4)", transition: "width 0.6s ease", borderRadius: 3 }} />
              </div>
              <div style={{ fontSize: 10, color: "#1e3a5f" }}>Target: ≥60% hit ratio</div>
            </div>
          </div>
        </div>

        {/* Chart + Log row */}
        <div style={{ display: "grid", gridTemplateColumns: "1fr 300px", gap: 12, marginBottom: 14 }}>

          {/* Recharts area chart */}
          <div style={{ background: "#0e1526", border: "1px solid #1a2640", borderRadius: 12, padding: "18px 22px" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
              <div>
                <div style={{ fontWeight: 600, fontSize: 14 }}>Avg Latency Trend</div>
                <div style={{ fontSize: 11, color: "#334155" }}>{chart.length} datapoints · 2s interval · session avg {sessionAvg}ms</div>
              </div>
              <span style={{ fontSize: 30, fontWeight: 800, color: "#10b981", fontFamily: "monospace" }}>
                {pipeline?.avgLatencyMs ?? "—"}<span style={{ fontSize: 14, color: "#475569", fontWeight: 400 }}>ms</span>
              </span>
            </div>
            <ResponsiveContainer width="100%" height={140}>
              <AreaChart data={chart} margin={{ top: 4, right: 4, left: -20, bottom: 0 }}>
                <defs>
                  <linearGradient id="avgGrad" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#10b981" stopOpacity={0.25} />
                    <stop offset="95%" stopColor="#10b981" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke="#1a2640" />
                <XAxis dataKey="t" tick={{ fill: "#334155", fontSize: 10 }} tickLine={false} axisLine={false} interval="preserveStartEnd" />
                <YAxis tick={{ fill: "#334155", fontSize: 10 }} tickLine={false} axisLine={false} unit="ms" />
                <Tooltip
                  contentStyle={{ background: "#0e1526", border: "1px solid #1a2640", borderRadius: 8, fontSize: 12 }}
                  labelStyle={{ color: "#475569" }}
                  itemStyle={{ color: "#10b981" }}
                  formatter={(v: number) => [`${v}ms`, "Avg"]}
                />
                <Area type="monotone" dataKey="avg" stroke="#10b981" strokeWidth={2} fill="url(#avgGrad)" dot={false} activeDot={{ r: 4, fill: "#10b981" }} />
              </AreaChart>
            </ResponsiveContainer>
          </div>

          {/* Activity log */}
          <div style={{ background: "#0e1526", border: "1px solid #1a2640", borderRadius: 12, padding: "18px 22px" }}>
            <div style={{ fontWeight: 600, fontSize: 14, marginBottom: 10, display: "flex", alignItems: "center", gap: 8 }}>
              <span style={{ width: 6, height: 6, borderRadius: "50%", background: "#10b981", display: "inline-block", animation: "pulse-dot 2s ease-in-out infinite" }} />
              Activity Log
              <span style={{ marginLeft: "auto", fontSize: 11, color: "#334155", fontWeight: 400 }}>{logs.length}</span>
            </div>
            <div style={{ display: "flex", flexDirection: "column", gap: 2, maxHeight: 162, overflowY: "auto", fontFamily: "'JetBrains Mono', monospace", fontSize: 11 }}>
              {logs.map((l, i) => (
                <div key={i} style={{ display: "flex", gap: 8, padding: "3px 6px", borderRadius: 4, background: i === 0 ? "#10b98112" : "transparent" }}>
                  <span style={{ color: "#334155", minWidth: 48, flexShrink: 0 }}>{l.time}</span>
                  <span style={{ color: statusColor(l.status), minWidth: 52, flexShrink: 0 }}>{l.status.replace("_", "·")}</span>
                  <span style={{ color: "#10b981", marginLeft: "auto" }}>{l.avgMs}ms</span>
                </div>
              ))}
              {logs.length === 0 && <div style={{ color: "#334155", padding: 8 }}>Waiting…</div>}
            </div>
          </div>
        </div>

        {/* Symbol table */}
        <div style={{ background: "#0e1526", border: "1px solid #1a2640", borderRadius: 12, padding: "18px 22px" }}>
          <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 12 }}>
            <div>
              <span style={{ fontWeight: 600, fontSize: 14 }}>Per-Symbol Latency</span>
              <span style={{ fontSize: 12, color: "#334155", marginLeft: 10 }}>Age of last cached event per symbol</span>
            </div>
            <div style={{ display: "flex", gap: 3, background: "#090d17", borderRadius: 7, padding: 3 }}>
              {(["ALL", "STOCK", "FOREX", "CRYPTO"] as const).map(f => (
                <button key={f} onClick={() => setFilter(f)} style={{ padding: "4px 10px", borderRadius: 5, border: "none", cursor: "pointer", fontSize: 11, fontWeight: 500, background: filter === f ? "#1a2640" : "transparent", color: filter === f ? "#e2e8f0" : "#475569", transition: "all 0.15s" }}>{f}</button>
              ))}
            </div>
          </div>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead>
              <tr style={{ borderBottom: "1px solid #1a2640" }}>
                {["Symbol", "Type", "Last Price", "Event Time", "Age", "Status"].map(h => (
                  <th key={h} style={{ padding: "6px 10px", textAlign: "left", fontSize: 10, fontWeight: 500, color: "#334155", textTransform: "uppercase", letterSpacing: "0.06em", whiteSpace: "nowrap" }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {filtered.map((s) => (
                <tr key={s.symbol} style={{ borderBottom: "1px solid #1a264015" }}
                  onMouseEnter={e => (e.currentTarget.style.background = "#1a264033")}
                  onMouseLeave={e => (e.currentTarget.style.background = "transparent")}
                >
                  <td style={{ padding: "8px 10px", fontWeight: 700, fontFamily: "monospace", fontSize: 13 }}>{s.symbol}</td>
                  <td style={{ padding: "8px 10px" }}>
                    <span style={{ background: `${typeColor(s.eventType)}18`, color: typeColor(s.eventType), padding: "2px 6px", borderRadius: 4, fontSize: 10, fontWeight: 600 }}>{s.eventType}</span>
                  </td>
                  <td style={{ padding: "8px 10px", fontFamily: "monospace", fontSize: 12 }}>{fmtPrice(s.lastPrice)}</td>
                  <td style={{ padding: "8px 10px", color: "#475569", fontSize: 11 }}>{fmtTime(s.eventTimestamp)}</td>
                  <td style={{ padding: "8px 10px", minWidth: 140 }}>
                    <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                      <div style={{ flex: 1, height: 3, background: "#1a2640", borderRadius: 2 }}>
                        <div style={{ width: `${Math.min(100, (s.ageMs / Math.max(pipeline?.maxLatencyMs ?? 1, 1)) * 100)}%`, height: "100%", background: s.ageMs < 100 ? "#10b981" : s.ageMs < 500 ? "#f59e0b" : "#ef4444", borderRadius: 2, transition: "width 0.4s ease" }} />
                      </div>
                      <span style={{ fontFamily: "monospace", fontSize: 11, color: statusColor(s.syncStatus), minWidth: 38, textAlign: "right" }}>{s.ageMs}ms</span>
                    </div>
                  </td>
                  <td style={{ padding: "8px 10px", fontSize: 11, fontWeight: 600, color: statusColor(s.syncStatus) }}>{s.syncStatus}</td>
                </tr>
              ))}
              {filtered.length === 0 && <tr><td colSpan={6} style={{ padding: 20, textAlign: "center", color: "#334155" }}>No data…</td></tr>}
            </tbody>
          </table>
        </div>
      </main>
    </div>
  );
}

function Stat({ label, value, color }: { label: string; value: string; color: string }) {
  return (
    <div style={{ display: "flex", flexDirection: "column" }}>
      <span style={{ fontSize: 10, color: "#334155" }}>{label}</span>
      <span style={{ fontSize: 16, fontWeight: 700, color, fontFamily: "monospace" }}>{value}</span>
    </div>
  );
}
