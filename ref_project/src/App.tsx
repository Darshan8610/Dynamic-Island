import PhoneDemo from "./components/PhoneDemo";
import RepoDocs from "./components/RepoDocs";

const NAV = [
  { href: "#demo", label: "demo" },
  { href: "#findings", label: "teardown" },
  { href: "#state-machine", label: "state-machine" },
  { href: "#physics", label: "physics" },
  { href: "#port", label: "android-port" },
];

export default function App() {
  return (
    <div className="min-h-screen bg-[#0a0c11] text-slate-200 antialiased">
      {/* ---------- repo top bar ---------- */}
      <header className="sticky top-0 z-50 border-b border-white/10 bg-[#0a0c11]/85 backdrop-blur-xl">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-5 py-3.5">
          <div className="flex min-w-0 items-center gap-2.5 font-mono text-sm">
            <svg viewBox="0 0 24 24" className="h-5 w-5 shrink-0 fill-emerald-400">
              <path d="M12 2a10 10 0 0 0-3.16 19.49c.5.09.68-.22.68-.48v-1.7c-2.78.6-3.37-1.34-3.37-1.34-.46-1.16-1.11-1.47-1.11-1.47-.91-.62.07-.6.07-.6 1 .07 1.53 1.03 1.53 1.03.9 1.52 2.34 1.08 2.91.83.09-.65.35-1.09.63-1.34-2.22-.25-4.56-1.11-4.56-4.94 0-1.1.39-1.99 1.03-2.69-.1-.25-.45-1.27.1-2.64 0 0 .84-.27 2.75 1.02a9.58 9.58 0 0 1 5 0c1.91-1.3 2.75-1.02 2.75-1.02.55 1.37.2 2.39.1 2.64.64.7 1.03 1.6 1.03 2.69 0 3.84-2.34 4.68-4.57 4.93.36.31.68.92.68 1.85V21c0 .27.18.58.69.48A10 10 0 0 0 12 2z" />
            </svg>
            <span className="truncate text-slate-400">
              rea<span className="text-slate-600">/</span>
              <span className="font-semibold text-white">dynamic-island-android</span>
            </span>
            <span className="hidden rounded-full border border-white/15 px-2 py-0.5 text-[11px] text-slate-400 sm:inline">
              Public
            </span>
          </div>
          <nav className="hidden items-center gap-5 font-mono text-[13px] text-slate-400 md:flex">
            {NAV.map((n) => (
              <a key={n.href} href={n.href} className="transition hover:text-emerald-300">
                {n.label}
              </a>
            ))}
          </nav>
          <div className="flex items-center gap-2 font-mono text-xs">
            <span className="flex items-center gap-1.5 rounded-lg border border-white/15 px-2.5 py-1.5 text-slate-300">
              Source repository
            </span>
            <span className="hidden items-center gap-1.5 rounded-lg border border-white/15 px-2.5 py-1.5 text-slate-300 sm:flex">
              Project history
            </span>
          </div>
        </div>
      </header>

      {/* ---------- hero ---------- */}
      <section className="relative overflow-hidden">
        <div className="pointer-events-none absolute -top-40 left-1/2 h-[500px] w-[800px] -translate-x-1/2 rounded-full bg-emerald-500/10 blur-[120px]" />
        <div className="relative mx-auto max-w-6xl px-5 pb-10 pt-16 text-center">
          <p className="mb-4 inline-flex items-center gap-2 rounded-full border border-emerald-400/30 bg-emerald-400/10 px-4 py-1.5 font-mono text-xs text-emerald-300">
            <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-emerald-400" />
            Design reference · Simulated content
          </p>
          <h1 className="mx-auto max-w-3xl text-4xl font-black leading-tight tracking-tight text-white sm:text-6xl">
            Dynamic Island,{" "}
            <span className="bg-gradient-to-r from-emerald-300 via-teal-300 to-sky-400 bg-clip-text text-transparent">
              ported to Android
            </span>
          </h1>
          <p className="mx-auto mt-5 max-w-2xl text-base leading-relaxed text-slate-400 sm:text-lg">
            This browser prototype illustrates activity states with simulated content. It is separate
            from the native Android app and does not demonstrate device performance or live system data.
          </p>
          <div className="mt-7 flex flex-wrap items-center justify-center gap-3 font-mono text-sm">
            <a href="#demo" className="rounded-xl bg-emerald-400 px-5 py-2.5 font-semibold text-black transition hover:bg-emerald-300 active:scale-95">
              ▶ Run live demo
            </a>
            <a href="#port" className="rounded-xl border border-white/15 px-5 py-2.5 text-slate-300 transition hover:border-emerald-400/50 hover:text-emerald-300 active:scale-95">
              Native implementation notes
            </a>
          </div>
          <p className="mt-8 text-sm text-slate-400">Design reference only. Project licensing is not asserted by this demo.</p>
        </div>
      </section>

      {/* ---------- live demo ---------- */}
      <section id="demo" className="scroll-mt-24 border-y border-white/10 bg-[#0d1017]">
        <div className="mx-auto max-w-6xl px-5 py-16">
          <p className="mb-1 font-mono text-xs uppercase tracking-[0.2em] text-emerald-400">00 · Replica</p>
          <h2 className="mb-8 text-2xl font-bold text-white">Interactive design reference</h2>
          <PhoneDemo />
        </div>
      </section>

      {/* ---------- docs ---------- */}
      <main className="mx-auto max-w-6xl px-5 py-20">
        <RepoDocs />
      </main>

      {/* ---------- footer ---------- */}
      <footer className="border-t border-white/10 py-10 text-center font-mono text-xs text-slate-500">
        <p>
          rea/dynamic-island-android · built by disassembling frames, not binaries ·{" "}
          <span className="text-slate-400">no Apple code was harmed</span>
        </p>
        <p className="mt-2 text-slate-600">Educational reverse-engineering case study. Not affiliated with Apple or Google.</p>
      </footer>
    </div>
  );
}
