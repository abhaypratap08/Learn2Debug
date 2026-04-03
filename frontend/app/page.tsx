'use client';

import { useState, useRef, useEffect } from 'react';
import Editor from '@monaco-editor/react';
import { AlertCircle, CheckCircle, Info, Zap, Copy, RotateCcw } from 'lucide-react';

interface Finding {
  severity: string;
  line: number;
  title: string;
  explanation: string;
  fixSuggestion: string;
  relatedDocumentation: string[];
}

const examples = [
  { name: "Division by Zero", code: "int result = 10 / 0;" },
  { name: "Null Pointer", code: "String s = null;\nSystem.out.println(s.length());" },
  { name: "String == Mistake", code: 'String a = "hello";\nif (a == "hello") {}' },
  { name: "Unbalanced Braces", code: "public class Test {\n    public static void main(String[] args) {\n        System.out.println(\"Hi\");\n" },
  { name: "Assignment in If", code: "int x = 5;\nif (x = 10) {}" },
];

export default function Home() {
  const [code, setCode] = useState(`class Solution {
    public static long sumScores(String s) {
        // your code here
    }
}`);
  const [result, setResult] = useState<any>(null);
  const [loading, setLoading] = useState(false);
  const [editorWidth, setEditorWidth] = useState(68); // percentage for normal 100% resolution
  const isDragging = useRef(false);

  const analyzeCode = async () => {
    setLoading(true);
    try {
      const res = await fetch('http://localhost:8080/api/analyze', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ level: 'beginner', code }),
      });
      const data = await res.json();
      setResult(data);
    } catch (err) {
      alert('Backend not reachable. Make sure Spring Boot is running on port 8080');
    }
    setLoading(false);
  };

  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text);
  };

  const getSeverityIcon = (severity: string) => {
    if (severity === 'ERROR') return <AlertCircle className="w-5 h-5 text-red-400" />;
    if (severity === 'WARNING') return <Info className="w-5 h-5 text-amber-400" />;
    return <CheckCircle className="w-5 h-5 text-emerald-400" />;
  };

  // Draggable panel
  const handleMouseDown = () => { isDragging.current = true; };
  const handleMouseMove = (e: MouseEvent) => {
    if (!isDragging.current) return;
    const newWidth = Math.max(40, Math.min(75, (e.clientX / window.innerWidth) * 100));
    setEditorWidth(newWidth);
  };
  const handleMouseUp = () => { isDragging.current = false; };

  useEffect(() => {
    window.addEventListener('mousemove', handleMouseMove);
    window.addEventListener('mouseup', handleMouseUp);
    return () => {
      window.removeEventListener('mousemove', handleMouseMove);
      window.removeEventListener('mouseup', handleMouseUp);
    };
  }, []);

  return (
    <div className="min-h-screen bg-[#111111] text-[#F5EDE3] flex flex-col font-sans">
      {/* Header */}
      <header className="bg-[#2C211B] border-b border-[#A67B5B]/30 px-8 py-5 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 bg-[#A67B5B] rounded-2xl flex items-center justify-center shadow-inner">
            <Zap className="w-6 h-6 text-[#111111]" />
          </div>
          <h1 className="text-3xl font-semibold tracking-tight">Learn2Debug</h1>
        </div>
      </header>

      <div className="flex flex-1 overflow-hidden">
        {/* Editor Panel */}
        <div style={{ width: `${editorWidth}%` }} className="flex flex-col border-r border-[#A67B5B]/20">
          <div className="px-8 py-4 bg-[#2C211B] border-b border-[#A67B5B]/30 flex items-center justify-between">
            <div className="flex items-center gap-3">
              <div className="w-3 h-3 bg-[#A67B5B] rounded-full"></div>
              <span className="font-medium text-lg">Main.java</span>
            </div>

            <div className="flex gap-2">
              {examples.map((ex, i) => (
                <button
                  key={i}
                  onClick={() => setCode(ex.code)}
                  className="px-5 py-2 text-sm bg-[#3C2A20] hover:bg-[#A67B5B] hover:text-[#111111] rounded-3xl transition-all"
                >
                  {ex.name}
                </button>
              ))}
            </div>

            <button
              onClick={() => { setCode(''); setResult(null); }}
              className="flex items-center gap-2 text-[#F5EDE3]/70 hover:text-[#F5EDE3] transition-colors"
            >
              <RotateCcw className="w-4 h-4" />
              Clear
            </button>
          </div>

          <div className="flex-1 p-6">
            <Editor
              height="100%"
              language="java"
              theme="vs-dark"
              value={code}
              onChange={(value) => setCode(value || '')}
              options={{
                fontSize: 16.5,
                minimap: { enabled: false },
                scrollBeyondLastLine: false,
                wordWrap: 'on',
                lineNumbers: 'on',
                automaticLayout: true,
              }}
            />
          </div>

          <div className="p-6 border-t border-[#A67B5B]/30 bg-[#2C211B]">
            <button
              onClick={analyzeCode}
              disabled={loading}
              className="w-full py-5 text-lg font-semibold bg-[#A67B5B] hover:bg-[#C19A6B] text-[#111111] rounded-3xl transition-all flex items-center justify-center gap-3"
            >
              {loading ? 'Analyzing...' : 'Analyze Code'}
            </button>
          </div>
        </div>

        {/* Draggable Divider */}
        <div
          onMouseDown={handleMouseDown}
          className="w-1 bg-[#A67B5B]/30 hover:bg-[#A67B5B] cursor-col-resize transition-colors flex items-center justify-center group"
        >
          <div className="w-1 h-12 bg-[#A67B5B]/40 group-hover:bg-[#A67B5B] rounded-full"></div>
        </div>

        {/* Analysis Panel */}
        <div className="flex-1 min-w-[400px] bg-[#2C211B] flex flex-col">
          <div className="px-8 py-6 border-b border-[#A67B5B]/30">
            <h2 className="text-2xl font-semibold flex items-center gap-3 text-[#F5EDE3]">
              <Zap className="w-6 h-6 text-[#A67B5B]" />
              Analysis Results
            </h2>
          </div>

          {result ? (
            <div className="flex-1 overflow-auto p-8 space-y-8">
              {/* Score */}
              <div className="text-center">
                <div className="text-sm text-[#F5EDE3]/70 mb-1">Your Score</div>
                <div className="text-7xl font-light text-[#F5EDE3] tracking-tighter">{result.score}</div>
              </div>

              {/* Findings */}
              <div className="space-y-6">
                {result.findings.map((finding: Finding, i: number) => (
                  <div key={i} className="bg-[#111111] border border-[#A67B5B]/30 rounded-3xl p-6">
                    <div className="flex gap-4">
                      {getSeverityIcon(finding.severity)}
                      <div className="flex-1">
                        <h3 className="font-medium text-lg text-[#F5EDE3]">{finding.title}</h3>
                        <p className="text-[#F5EDE3]/80 mt-3 text-[15px] leading-relaxed">{finding.explanation}</p>
                        
                        <div className="mt-5 bg-[#2C211B] rounded-2xl p-5 text-[#F5EDE3] text-sm">
                          💡 {finding.fixSuggestion}
                        </div>

                        <button
                          onClick={() => copyToClipboard(finding.fixSuggestion)}
                          className="mt-4 flex items-center gap-2 text-sm text-[#F5EDE3]/70 hover:text-[#A67B5B] transition-colors"
                        >
                          <Copy className="w-4 h-4" />
                          Copy fix suggestion
                        </button>

                        {finding.relatedDocumentation.length > 0 && (
                          <div className="mt-6">
                            <p className="text-xs text-[#F5EDE3]/60 mb-3">Learn more from Oracle:</p>
                            <div className="flex flex-wrap gap-2">
                              {finding.relatedDocumentation.map((link, idx) => (
                                <a
                                  key={idx}
                                  href={link}
                                  target="_blank"
                                  className="px-5 py-2 bg-[#3C2A20] hover:bg-[#A67B5B] hover:text-[#111111] rounded-2xl transition-colors text-xs"
                                >
                                  📖 Documentation
                                </a>
                              ))}
                            </div>
                          </div>
                        )}
                      </div>
                    </div>
                  </div>
                ))}
              </div>

              <div className="text-xs text-[#F5EDE3]/60 text-center pt-4 border-t border-[#A67B5B]/20">
                {result.summary}
              </div>
            </div>
          ) : (
            <div className="flex-1 flex flex-col items-center justify-center text-center px-10">
              <div className="w-20 h-20 bg-[#3C2A20] rounded-3xl flex items-center justify-center mb-8">
                <Zap className="w-10 h-10 text-[#A67B5B]/60" />
              </div>
              <p className="text-2xl text-[#F5EDE3]">Your analysis will appear here</p>
              <p className="text-[#F5EDE3]/60 mt-3 max-w-xs">Paste your Java code on the left and click Analyze</p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
