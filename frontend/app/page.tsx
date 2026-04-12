'use client';

import { useEffect, useRef, useState } from 'react';
import Editor from '@monaco-editor/react';
import {
  AlertCircle,
  CheckCircle2,
  Copy,
  ExternalLink,
        Info,
        LoaderCircle,
        RotateCcw,
        Sparkles,
} from 'lucide-react';

interface Finding {
  severity: string;
  line: number;
  title: string;
  explanation: string;
  fixSuggestion: string;
  relatedDocumentation: string[];
  aiInsight?: {
    summary: string;
    likelyCause: string;
    confidence: string;
  } | null;
}

interface AnalysisResponse {
  summary: string;
  score: number;
  level: AnalysisLevel;
  checksApplied: number;
  findings: Finding[];
}

type AnalysisLevel = 'beginner' | 'intermediate' | 'advanced';
type FindingCountKey = 'ERROR' | 'WARNING' | 'TIP';

const starterCode = `class Solution {
    public static long sumScores(String s) {
        // Write Java code here, then analyze it.
        return 0L;
    }
}`;
const defaultAnalysisLevel: AnalysisLevel = 'beginner';

const defaultApiUrl = 'https://learn2debug-api.onrender.com';

function isLocalDevelopmentHost(hostname: string) {
  return (
    hostname === 'localhost' ||
    hostname === '::1' ||
    /^127(?:\.\d{1,3}){3}$/.test(hostname) ||
    /^10(?:\.\d{1,3}){3}$/.test(hostname) ||
    /^192\.168(?:\.\d{1,3}){2}$/.test(hostname) ||
    /^172\.(1[6-9]|2\d|3[01])(?:\.\d{1,3}){2}$/.test(hostname) ||
    hostname.endsWith('.local')
  );
}

function formatHostForUrl(hostname: string) {
  return hostname.includes(':') ? `[${hostname}]` : hostname;
}

function resolveApiBaseUrl() {
  const configured = process.env.NEXT_PUBLIC_API_URL?.trim();

  if (configured) {
    return configured.replace(/\/$/, '');
  }

  if (typeof window !== 'undefined') {
    const { hostname } = window.location;
    if (isLocalDevelopmentHost(hostname)) {
      return `http://${formatHostForUrl(hostname)}:8080`;
    }
  }

  return defaultApiUrl;
}

function severityMeta(severity: string) {
  switch (severity) {
    case 'ERROR':
      return {
        label: 'Error',
        classes: 'border-red-500/30 bg-red-500/10 text-red-100',
        icon: <AlertCircle className="h-5 w-5 text-red-300" />,
      };
    case 'WARNING':
      return {
        label: 'Warning',
        classes: 'border-amber-500/30 bg-amber-500/10 text-amber-100',
        icon: <Info className="h-5 w-5 text-amber-300" />,
      };
    default:
      return {
        label: 'Tip',
        classes: 'border-emerald-500/30 bg-emerald-500/10 text-emerald-100',
        icon: <CheckCircle2 className="h-5 w-5 text-emerald-300" />,
      };
  }
}

function documentationLabel(link: string) {
  try {
    const host = new URL(link).hostname.replace(/^www\./, '');
    if (host.includes('oracle')) return 'Oracle docs';
    if (host.includes('junit')) return 'JUnit guide';
    return host;
  } catch {
    return 'Docs';
  }
}

function findingCountSummary(findings: Finding[]) {
  return findings.reduce<Record<FindingCountKey, number>>(
    (counts, finding) => {
      if (finding.severity === 'ERROR' || finding.severity === 'WARNING' || finding.severity === 'TIP') {
        counts[finding.severity] += 1;
      }
      return counts;
    },
    { ERROR: 0, WARNING: 0, TIP: 0 },
  );
}

export default function Home() {
  const [code, setCode] = useState(starterCode);
  const [result, setResult] = useState<AnalysisResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [copiedText, setCopiedText] = useState<string | null>(null);
  const [apiBaseUrl, setApiBaseUrl] = useState(defaultApiUrl);
  const abortControllerRef = useRef<AbortController | null>(null);
  const copyTimeoutRef = useRef<number | null>(null);

  useEffect(() => {
    setApiBaseUrl(resolveApiBaseUrl());

    return () => {
      abortControllerRef.current?.abort();
      if (copyTimeoutRef.current !== null) {
        window.clearTimeout(copyTimeoutRef.current);
      }
    };
  }, []);

  const analyzeCode = async () => {
    if (!code.trim()) {
      setError('Paste some Java code before running analysis.');
      setResult(null);
      return;
    }

    setLoading(true);
    setError(null);
    abortControllerRef.current?.abort();
    const controller = new AbortController();
    abortControllerRef.current = controller;

    try {
      const response = await fetch(`${apiBaseUrl}/api/analyze`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ level: defaultAnalysisLevel, code }),
        signal: controller.signal,
      });

      const payload = await response.json().catch(() => null);

      if (!response.ok) {
        const message =
          payload?.errors?.code ||
          payload?.message ||
          'Analysis failed. Check that the backend is running and reachable.';
        throw new Error(message);
      }

      setResult(payload);
    } catch (requestError) {
      if (requestError instanceof Error && requestError.name === 'AbortError') {
        return;
      }
      const message =
        requestError instanceof Error
          ? requestError.message
          : 'Could not connect to the backend.';
      setError(message);
      setResult(null);
    } finally {
      if (abortControllerRef.current === controller) {
        abortControllerRef.current = null;
        setLoading(false);
      }
    }
  };

  const copyToClipboard = async (text: string) => {
    try {
      await navigator.clipboard.writeText(text);
      if (copyTimeoutRef.current !== null) {
        window.clearTimeout(copyTimeoutRef.current);
      }
      setCopiedText(text);
      copyTimeoutRef.current = window.setTimeout(() => {
        setCopiedText(null);
        copyTimeoutRef.current = null;
      }, 1500);
    } catch {
      setError('Clipboard access failed in this browser.');
    }
  };

  const resetWorkspace = () => {
    setCode(starterCode);
    setResult(null);
    setError(null);
  };

  const scoreTone =
    result && result.score >= 90
      ? 'text-emerald-200'
      : result && result.score >= 60
        ? 'text-amber-100'
        : 'text-red-100';
  const codeLineCount = code.split(/\r?\n/).length;
  const findingCounts = result ? findingCountSummary(result.findings) : null;

  return (
    <main className="min-h-screen bg-[radial-gradient(circle_at_top_left,_rgba(243,177,92,0.16),_transparent_24%),linear-gradient(180deg,_#16110f_0%,_#0f0b0a_100%)] text-stone-100 lg:h-screen lg:overflow-hidden">
      <div className="mx-auto flex min-h-screen max-w-[1580px] flex-col px-4 py-4 sm:px-6 lg:h-screen lg:min-h-0 lg:px-8 lg:py-4">
        <header className="mb-4 rounded-[1.6rem] border border-stone-800/80 bg-[linear-gradient(135deg,rgba(34,24,22,0.92),rgba(18,13,12,0.82))] px-4 py-3 shadow-[0_20px_80px_rgba(0,0,0,0.28)] backdrop-blur sm:px-5">
          <div className="min-w-0">
            <div className="flex items-center gap-2">
              <div className="inline-flex items-center gap-2 rounded-full border border-amber-400/20 bg-amber-300/10 px-2.5 py-1 text-[11px] uppercase tracking-[0.22em] text-amber-100/80">
                <Sparkles className="h-3.5 w-3.5" />
                Learn2Debug
              </div>
              <span className="hidden text-sm text-stone-400 sm:inline">Java static analysis</span>
            </div>
            <p className="mt-2 text-sm text-stone-300">Paste Java. Run analysis. Fix faster.</p>
          </div>
        </header>

        <section className="grid flex-1 gap-4 lg:min-h-0 lg:grid-cols-[minmax(0,1.02fr)_minmax(0,0.98fr)]">
          <div className="overflow-hidden rounded-[2rem] border border-stone-800/80 bg-stone-950/70 shadow-[0_24px_80px_rgba(0,0,0,0.22)] lg:flex lg:min-h-0 lg:flex-col">
            <div className="border-b border-stone-800/90 px-5 py-3.5 sm:px-6">
              <div className="flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
                <div>
                  <div className="text-[11px] uppercase tracking-[0.24em] text-stone-500">Workspace</div>
                  <div className="mt-1 text-xl font-semibold text-stone-50">Java editor</div>
                </div>

                <div className="rounded-[1.3rem] border border-stone-800 bg-stone-900/70 px-4 py-2.5">
                  <div className="text-[11px] uppercase tracking-[0.2em] text-stone-500">Input</div>
                  <div className="mt-1 text-lg font-semibold text-stone-50">{codeLineCount} line{codeLineCount === 1 ? '' : 's'}</div>
                </div>
              </div>
            </div>

            <div className="flex-1 border-b border-stone-800/90 p-3 sm:p-4 lg:min-h-0">
              <div className="h-[360px] overflow-hidden rounded-[1.7rem] border border-stone-800/90 bg-[#120f10] shadow-[inset_0_1px_0_rgba(255,255,255,0.02)] sm:h-[420px] lg:h-full">
                <Editor
                  height="100%"
                  defaultLanguage="java"
                  theme="vs-dark"
                  value={code}
                  onChange={(value) => setCode(value || '')}
                  options={{
                    automaticLayout: true,
                    fontSize: 15,
                    minimap: { enabled: false },
                    padding: { top: 20 },
                    scrollBeyondLastLine: false,
                    wordWrap: 'on',
                  }}
                />
              </div>
            </div>

            <div className="px-5 py-3.5 sm:px-6">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div className="text-sm text-stone-500">Beginner scan</div>
                <div className="flex flex-wrap gap-3">
                  <button
                    onClick={resetWorkspace}
                    className="inline-flex items-center gap-2 rounded-full border border-stone-700 px-4 py-2 text-sm text-stone-200 transition hover:border-stone-500 hover:bg-stone-900"
                  >
                    <RotateCcw className="h-4 w-4" />
                    Reset
                  </button>
                  <button
                    onClick={analyzeCode}
                    disabled={loading}
                    className="inline-flex min-w-[156px] items-center justify-center gap-2 rounded-full bg-amber-300 px-5 py-2.5 text-sm font-semibold text-stone-950 transition hover:bg-amber-200 disabled:cursor-not-allowed disabled:bg-amber-100/70"
                  >
                    {loading ? <LoaderCircle className="h-4 w-4 animate-spin" /> : <Sparkles className="h-4 w-4" />}
                    {loading ? 'Analyzing...' : 'Analyze code'}
                  </button>
                </div>
              </div>
            </div>
          </div>

          <aside className="overflow-hidden rounded-[2rem] border border-stone-800/80 bg-stone-950/70 shadow-[0_24px_80px_rgba(0,0,0,0.22)] lg:flex lg:min-h-0 lg:flex-col">
            <div className="border-b border-stone-800/90 px-5 py-3.5 sm:px-6">
              <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
                <div>
                  <div className="text-[11px] uppercase tracking-[0.24em] text-stone-500">Inspector</div>
                  <h2 className="mt-1 text-xl font-semibold text-stone-50">Results</h2>
                </div>
                {result ? (
                  <div className="rounded-[1.3rem] border border-stone-700 bg-stone-900 px-4 py-2.5 text-center">
                    <div className="text-xs uppercase tracking-[0.2em] text-stone-500">Score</div>
                    <div className={`mt-1 text-3xl font-semibold tracking-[-0.04em] ${scoreTone}`}>{result.score}</div>
                  </div>
                ) : null}
              </div>
            </div>

            <div className="px-5 py-4 sm:px-6 lg:min-h-0 lg:flex-1 lg:overflow-auto">
              {error ? (
                <div className="mb-4 rounded-[1.7rem] border border-red-500/30 bg-red-500/10 px-4 py-3 text-sm text-red-100">
                  {error}
                </div>
              ) : null}

              {result ? (
                <div className="space-y-4">
                  <div className="rounded-[1.5rem] border border-stone-800 bg-[linear-gradient(180deg,rgba(255,255,255,0.02),rgba(255,255,255,0))] px-4 py-3 text-sm text-stone-300">
                    <p className="leading-6 text-stone-300">{result.summary}</p>
                  </div>

                  {findingCounts ? (
                    <div className="grid gap-3 sm:grid-cols-3">
                      <div className="rounded-[1.5rem] border border-red-500/20 bg-red-500/8 px-4 py-3">
                        <div className="text-[11px] uppercase tracking-[0.2em] text-red-100/60">Errors</div>
                        <div className="mt-2 text-2xl font-semibold text-red-100">{findingCounts.ERROR}</div>
                      </div>
                      <div className="rounded-[1.5rem] border border-amber-400/20 bg-amber-400/8 px-4 py-3">
                        <div className="text-[11px] uppercase tracking-[0.2em] text-amber-100/60">Warnings</div>
                        <div className="mt-2 text-2xl font-semibold text-amber-100">{findingCounts.WARNING}</div>
                      </div>
                      <div className="rounded-[1.5rem] border border-emerald-400/20 bg-emerald-400/8 px-4 py-3">
                        <div className="text-[11px] uppercase tracking-[0.2em] text-emerald-100/60">Tips</div>
                        <div className="mt-2 text-2xl font-semibold text-emerald-100">{findingCounts.TIP}</div>
                      </div>
                    </div>
                  ) : null}

                  <div className="space-y-3">
                    {result.findings.map((finding, index) => {
                      const meta = severityMeta(finding.severity);

                      return (
                        <article
                          key={`${finding.title}-${index}`}
                          className={`rounded-[1.9rem] border px-4 py-4 ${meta.classes}`}
                        >
                          <div className="flex items-start gap-3">
                            <div className="mt-0.5">{meta.icon}</div>
                            <div className="min-w-0 flex-1">
                              <div className="flex flex-wrap items-center gap-2">
                                <h3 className="text-base font-semibold text-white">{finding.title}</h3>
                                <span className="rounded-full border border-white/10 px-2 py-1 text-[11px] uppercase tracking-[0.18em] text-white/70">
                                  {meta.label}
                                </span>
                                <span className="rounded-full border border-white/10 px-2 py-1 text-[11px] uppercase tracking-[0.18em] text-white/70">
                                  Line {finding.line}
                                </span>
                              </div>

                              <p className="mt-3 text-sm leading-6 text-white/85">{finding.explanation}</p>

                              {finding.aiInsight ? (
                                <div className="mt-4 rounded-[1.4rem] border border-white/10 bg-black/15 px-4 py-3">
                                  <div className="flex flex-wrap items-center gap-2">
                                    <div className="text-xs uppercase tracking-[0.2em] text-white/55">AI explanation</div>
                                    <span className="rounded-full border border-white/10 px-2 py-1 text-[11px] uppercase tracking-[0.18em] text-white/70">
                                      {finding.aiInsight.confidence} confidence
                                    </span>
                                  </div>
                                  <p className="mt-2 text-sm leading-6 text-white/90">{finding.aiInsight.summary}</p>
                                  <p className="mt-2 text-sm leading-6 text-white/70">
                                    Likely cause: {finding.aiInsight.likelyCause}
                                  </p>
                                </div>
                              ) : null}

                              <div className="mt-4 rounded-[1.4rem] bg-black/20 px-4 py-3">
                                <div className="text-xs uppercase tracking-[0.2em] text-white/55">Suggested fix</div>
                                <p className="mt-1 text-sm leading-6 text-white/90">{finding.fixSuggestion}</p>
                              </div>

                              <div className="mt-4 flex flex-wrap items-center gap-2">
                                <button
                                  onClick={() => copyToClipboard(finding.fixSuggestion)}
                                  className="inline-flex items-center gap-2 rounded-full border border-white/10 px-3 py-2 text-sm text-white/85 transition hover:bg-white/10"
                                >
                                  <Copy className="h-4 w-4" />
                                  {copiedText === finding.fixSuggestion ? 'Copied' : 'Copy fix'}
                                </button>

                                {finding.relatedDocumentation.map((link) => (
                                  <a
                                    key={link}
                                    href={link}
                                    target="_blank"
                                    rel="noreferrer"
                                    className="inline-flex items-center gap-2 rounded-full border border-white/10 px-3 py-2 text-sm text-white/85 transition hover:bg-white/10"
                                  >
                                    <ExternalLink className="h-4 w-4" />
                                    {documentationLabel(link)}
                                  </a>
                                ))}
                              </div>
                            </div>
                          </div>
                        </article>
                      );
                    })}
                  </div>
                </div>
              ) : (
                <div className="flex min-h-[420px] flex-col items-center justify-center rounded-[2rem] border border-dashed border-stone-800 bg-[linear-gradient(180deg,rgba(255,255,255,0.015),rgba(255,255,255,0))] px-6 text-center">
                  <div className="rounded-[1.5rem] border border-stone-800 bg-stone-950 p-4">
                    <Sparkles className="h-8 w-8 text-amber-200" />
                  </div>
                  <h3 className="mt-5 text-xl font-semibold text-stone-100">No analysis yet</h3>
                  <p className="mt-2 max-w-sm text-sm leading-6 text-stone-400">
                    Run the analyzer to see findings, fixes, and docs.
                  </p>
                </div>
              )}
            </div>
          </aside>
        </section>
      </div>
    </main>
  );
}
