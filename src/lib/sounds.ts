// Мягкие синтезированные звуки напоминаний (Web Audio, без внешних файлов).
// Отвечает на отзыв «берётся стандартный звук из будильника — это ужас».

export type SoundOption = { id: string; label: string; hint: string };

export const SOUND_OPTIONS: SoundOption[] = [
  { id: "chime", label: "Колокольчик", hint: "мягкий двойной звон" },
  { id: "carillon", label: "КариЛЬОН", hint: "нежное арпеджио" },
  { id: "marimba", label: "Маримба", hint: "тёплые деревянные ноты" },
  { id: "birds", label: "Птички", hint: "лёгкое щебетание" },
  { id: "pulse", label: "Деликатный зуммер", hint: "два тихих сигнала" },
  { id: "wave", label: "Спокойная волна", hint: "мягкий шум прибоя" },
];

export function soundLabel(id: string | null | undefined): string {
  return SOUND_OPTIONS.find((s) => s.id === id)?.label ?? "Колокольчик";
}

let ctx: AudioContext | null = null;

function getCtx(): AudioContext | null {
  if (typeof window === "undefined") return null;
  try {
    const AC = window.AudioContext ?? (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
    if (!AC) return null;
    if (!ctx) ctx = new AC();
    if (ctx.state === "suspended") void ctx.resume();
    return ctx;
  } catch {
    return null;
  }
}

// Огибающая: мгновенный подъём и плавный спад
function pluck(ac: AudioContext, out: GainNode, type: OscillatorType, freq: number, t0: number, dur: number, peak: number) {
  const osc = ac.createOscillator();
  const g = ac.createGain();
  osc.type = type;
  osc.frequency.setValueAtTime(freq, t0);
  g.gain.setValueAtTime(0.0001, t0);
  g.gain.exponentialRampToValueAtTime(peak, t0 + 0.012);
  g.gain.exponentialRampToValueAtTime(0.0001, t0 + dur);
  osc.connect(g).connect(out);
  osc.start(t0);
  osc.stop(t0 + dur + 0.05);
}

function chirp(ac: AudioContext, out: GainNode, t0: number, f1: number, f2: number, dur: number) {
  const osc = ac.createOscillator();
  const g = ac.createGain();
  osc.type = "sine";
  osc.frequency.setValueAtTime(f1, t0);
  osc.frequency.exponentialRampToValueAtTime(f2, t0 + dur * 0.6);
  osc.frequency.exponentialRampToValueAtTime(f1 * 0.9, t0 + dur);
  g.gain.setValueAtTime(0.0001, t0);
  g.gain.exponentialRampToValueAtTime(0.16, t0 + 0.02);
  g.gain.exponentialRampToValueAtTime(0.0001, t0 + dur);
  osc.connect(g).connect(out);
  osc.start(t0);
  osc.stop(t0 + dur + 0.05);
}

const SYNTH: Record<string, (ac: AudioContext, out: GainNode) => void> = {
  chime: (ac, out) => {
    const t = ac.currentTime + 0.02;
    pluck(ac, out, "sine", 1318.5, t, 1.1, 0.22);
    pluck(ac, out, "sine", 2637, t, 0.7, 0.05);
    pluck(ac, out, "sine", 1568, t + 0.22, 1.2, 0.22);
    pluck(ac, out, "sine", 3136, t + 0.22, 0.6, 0.04);
  },
  carillon: (ac, out) => {
    const t = ac.currentTime + 0.02;
    [1046.5, 1318.5, 1568, 2093].forEach((f, i) => {
      pluck(ac, out, "sine", f, t + i * 0.14, 1.3, 0.16);
      pluck(ac, out, "sine", f * 2, t + i * 0.14, 0.5, 0.03);
    });
  },
  marimba: (ac, out) => {
    const t = ac.currentTime + 0.02;
    [1046.5, 880, 698.5].forEach((f, i) => {
      pluck(ac, out, "sine", f, t + i * 0.16, 0.5, 0.3);
      pluck(ac, out, "triangle", f, t + i * 0.16, 0.25, 0.12);
    });
  },
  birds: (ac, out) => {
    const t = ac.currentTime + 0.02;
    chirp(ac, out, t, 2600, 3800, 0.16);
    chirp(ac, out, t + 0.22, 2900, 4100, 0.14);
    chirp(ac, out, t + 0.46, 2400, 3400, 0.2);
  },
  pulse: (ac, out) => {
    const t = ac.currentTime + 0.02;
    const lp = ac.createBiquadFilter();
    lp.type = "lowpass";
    lp.frequency.value = 1400;
    lp.connect(out);
    pluck(ac, lp as unknown as GainNode, "triangle", 640, t, 0.14, 0.2);
    pluck(ac, lp as unknown as GainNode, "triangle", 640, t + 0.2, 0.18, 0.16);
  },
  wave: (ac, out) => {
    const t = ac.currentTime + 0.02;
    const dur = 1.8;
    const buf = ac.createBuffer(1, Math.ceil(ac.sampleRate * dur), ac.sampleRate);
    const data = buf.getChannelData(0);
    for (let i = 0; i < data.length; i++) data[i] = Math.random() * 2 - 1;
    const src = ac.createBufferSource();
    src.buffer = buf;
    const bp = ac.createBiquadFilter();
    bp.type = "bandpass";
    bp.Q.value = 0.8;
    bp.frequency.setValueAtTime(350, t);
    bp.frequency.exponentialRampToValueAtTime(900, t + dur * 0.5);
    bp.frequency.exponentialRampToValueAtTime(350, t + dur);
    const g = ac.createGain();
    g.gain.setValueAtTime(0.0001, t);
    g.gain.exponentialRampToValueAtTime(0.14, t + dur * 0.4);
    g.gain.exponentialRampToValueAtTime(0.0001, t + dur);
    src.connect(bp).connect(g).connect(out);
    src.start(t);
  },
};

export function playSound(id: string | null | undefined) {
  const ac = getCtx();
  if (!ac) return;
  const master = ac.createGain();
  master.gain.value = 0.9;
  master.connect(ac.destination);
  (SYNTH[id ?? "chime"] ?? SYNTH.chime)(ac, master);
}
