import React, {
  useRef,
  useState,
  useMemo,
  useCallback,
  useEffect,
  type CSSProperties
} from 'react';
import rawHtml from './sources/brand-orbs-v2.html.js';

export const BRAND_ORB_VARIANTS = [
  'claude',
  'openai',
  'codex',
  'cursor',
  'gemini',
  'figma',
  'framer',
  'react',
  'swift',
  'designcode',
  'aura',
  'dreamcut',
  'ui',
  'ux',
  'css',
  'ios',
  'neuform',
  'github',
  'x',
  'instagram',
  'threads',
  'linkedin',
  'email'
] as const;

export const BRAND_ORB_SIZES = ['small', 'medium'] as const;

export type BrandOrbVariant = (typeof BRAND_ORB_VARIANTS)[number];
export type BrandOrbSize = (typeof BRAND_ORB_SIZES)[number];
export type BrandOrbMode = 'auto' | 'dark' | 'light';

export interface BrandOrbsProps {
  variant?: BrandOrbVariant;
  size?: BrandOrbSize;
  mode?: BrandOrbMode;
  speed?: number;
  paused?: boolean;
  'aria-label'?: string;
  className?: string;
  style?: CSSProperties;
}

export const BRAND_ORBS_DEFAULTS = {
  variant: 'aura' as BrandOrbVariant,
  size: 'medium' as BrandOrbSize,
  mode: 'dark' as BrandOrbMode,
  speed: 1,
  paused: false
} as const;

const SIZE_MAP: Record<BrandOrbSize, number> = {
  small: 20,
  medium: 56
};

const LABELS: Record<BrandOrbVariant, string> = {
  claude: 'Claude Code',
  openai: 'OpenAI',
  codex: 'Codex',
  cursor: 'Cursor',
  gemini: 'Gemini',
  figma: 'Figma',
  framer: 'Framer',
  react: 'React',
  swift: 'Swift',
  designcode: 'DesignCode',
  aura: 'Aura',
  dreamcut: 'DreamCut',
  ui: 'UI',
  ux: 'UX',
  css: 'CSS',
  ios: 'iOS',
  neuform: 'Neuform',
  github: 'GitHub',
  x: 'X',
  instagram: 'Instagram',
  threads: 'Threads',
  linkedin: 'LinkedIn',
  email: 'Email'
};

function clamp(val: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, val));
}

function resolveSystemTheme(): 'dark' | 'light' {
  if (typeof document === 'undefined' || typeof window === 'undefined') return 'dark';
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

function extractScript(html: string): string {
  const matches = [...html.matchAll(/<script(?:\s[^>]*)?>([\s\S]*?)<\/script>/gi)];
  return matches.at(-1)?.[1] ?? '';
}

const EXTRACTED_SCRIPT = extractScript(rawHtml as string).replace(/<\/script/gi, '<\\/script');

function buildSrcDoc(variant: BrandOrbVariant, size: BrandOrbSize, mode: 'dark' | 'light'): string {
  const bg = mode === 'light' ? '#dad7cc' : '#050608';
  const filter = mode === 'light' ? 'invert(1) hue-rotate(180deg) contrast(1.04) saturate(.92)' : 'none';
  const px = SIZE_MAP[size] || 56;
  const modeAttr = JSON.stringify(variant).replace(/</g, '\\u003c');

  const controlsScript = `<script data-brand-orbs-controls>
(function () {
  var nativeNow = performance.now.bind(performance);
  var last = nativeNow();
  var virtual = last;
  var controls = { speed: 1, paused: false };
  window.__BRAND_ORB_PAUSED = false;
  performance.now = function () {
    var real = nativeNow();
    if (!controls.paused) virtual += (real - last) * controls.speed;
    last = real;
    return virtual;
  };
  window.addEventListener('message', function (event) {
    if (!event.data || event.data.type !== 'brand-orbs-controls') return;
    var next = event.data.controls || {};
    if (Number.isFinite(next.speed)) controls.speed = Math.max(.1, Math.min(3, next.speed));
    controls.paused = Boolean(next.paused);
    window.__BRAND_ORB_PAUSED = controls.paused;
  });
})();
<\\/script>`;

  const patchedScript = EXTRACTED_SCRIPT.replace(
    'if (document.visibilityState !== "hidden")',
    'if (document.visibilityState !== "hidden" && !window.__BRAND_ORB_PAUSED)'
  );

  return `<!doctype html>
<html lang="en" data-theme="${mode}">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${LABELS[variant]} Brand Orb</title>
<style>
html, body { width: 100%; height: 100%; margin: 0; overflow: hidden; background: ${bg}; }
body { display: grid; place-items: center; }
canvas { display: block; width: ${px}px; height: ${px}px; filter: ${filter}; }
</style>
${controlsScript}
</head>
<body>
<canvas data-mode=${modeAttr} data-size="${px}" aria-hidden="true"></canvas>
<script>${patchedScript}<\\/script>
</body>
</html>`;
}

export const BrandOrbs: React.FC<BrandOrbsProps> = ({
  variant = BRAND_ORBS_DEFAULTS.variant,
  size = BRAND_ORBS_DEFAULTS.size,
  mode = BRAND_ORBS_DEFAULTS.mode,
  speed = BRAND_ORBS_DEFAULTS.speed,
  paused = BRAND_ORBS_DEFAULTS.paused,
  'aria-label': ariaLabel,
  className,
  style
}) => {
  const iframeRef = useRef<HTMLIFrameElement>(null);
  const [isIntersecting, setIsIntersecting] = useState(true);
  const [isVisible, setIsVisible] = useState(() => typeof document === 'undefined' || !document.hidden);
  const [prefersReducedMotion, setPrefersReducedMotion] = useState(false);

  // Check reduced motion
  useEffect(() => {
    if (typeof window === 'undefined') return;
    const media = window.matchMedia('(prefers-reduced-motion: reduce)');
    setPrefersReducedMotion(media.matches);
    const handler = (e: MediaQueryListEvent) => setPrefersReducedMotion(e.matches);
    media.addEventListener('change', handler);
    return () => media.removeEventListener('change', handler);
  }, []);

  const resolvedMode: 'dark' | 'light' = mode === 'auto' ? resolveSystemTheme() : mode;
  const safeVariant: BrandOrbVariant = BRAND_ORB_VARIANTS.includes(variant) ? variant : 'aura';
  const safeSize: BrandOrbSize = BRAND_ORB_SIZES.includes(size) ? size : 'medium';
  const clampedSpeed = clamp(speed, 0.1, 3);
  const effectivePaused = paused || !isIntersecting || !isVisible || prefersReducedMotion;

  const srcDoc = useMemo(
    () => buildSrcDoc(safeVariant, safeSize, resolvedMode),
    [safeVariant, safeSize, resolvedMode]
  );

  const postControls = useCallback(() => {
    iframeRef.current?.contentWindow?.postMessage(
      {
        type: 'brand-orbs-controls',
        controls: { speed: clampedSpeed, paused: effectivePaused }
      },
      '*'
    );
  }, [effectivePaused, clampedSpeed]);

  // IntersectionObserver to pause when offscreen
  useEffect(() => {
    const el = iframeRef.current;
    if (!el || typeof IntersectionObserver === 'undefined') return;
    const observer = new IntersectionObserver(([entry]) => {
      setIsIntersecting(entry?.isIntersecting ?? true);
    });
    observer.observe(el);
    return () => observer.disconnect();
  }, []);

  // VisibilityChange listener
  useEffect(() => {
    if (typeof document === 'undefined') return;
    const handleVisibility = () => setIsVisible(!document.hidden);
    document.addEventListener('visibilitychange', handleVisibility);
    return () => document.removeEventListener('visibilitychange', handleVisibility);
  }, []);

  // Post controls on changes and srcDoc reloads
  useEffect(() => {
    postControls();
  }, [postControls, srcDoc]);

  const bg = resolvedMode === 'light' ? '#dad7cc' : '#050608';

  return (
    <iframe
      ref={iframeRef}
      className={className}
      title={ariaLabel ?? `${LABELS[safeVariant]} animated brand orb`}
      srcDoc={srcDoc}
      sandbox="allow-scripts"
      loading="eager"
      onLoad={postControls}
      style={{
        display: 'block',
        width: '100%',
        height: '100%',
        border: 0,
        background: bg,
        ...style
      }}
    />
  );
};

export default BrandOrbs;
