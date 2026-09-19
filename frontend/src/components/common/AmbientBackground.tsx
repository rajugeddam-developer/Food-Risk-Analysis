import React, { useEffect, useRef } from 'react';
import './ambient-background.css';

interface VerticalStreak {
  xPercent: number;
  width: number;
  heightRatio: number;
  speed: number;
  swayAmp: number;
  pulseSpeed: number;
  pulseAmp: number;
  baseOpacity: number;
  phase: number;
  colorHex: string;
  subRaysCount: number;
}

interface FloorRay {
  angle: number;
  length: number;
  opacity: number;
  width: number;
  color: string;
  shimmerSpeed: number;
  phase: number;
}

export const AmbientBackground: React.FC = () => {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const cursorLightRef = useRef<HTMLDivElement>(null);

  // --------------------------------------------------------------------------
  // 1. Interactive Cursor Spotlight (Desktop fine-pointer)
  // --------------------------------------------------------------------------
  useEffect(() => {
    const mediaQuery = window.matchMedia('(pointer: fine)');
    if (!mediaQuery.matches) return;

    let targetX = window.innerWidth / 2;
    let targetY = window.innerHeight / 3;
    let currentX = targetX;
    let currentY = targetY;
    let rafId: number;

    const handleMouseMove = (e: MouseEvent) => {
      targetX = e.clientX;
      targetY = e.clientY;
    };

    const updatePosition = () => {
      currentX += (targetX - currentX) * 0.05;
      currentY += (targetY - currentY) * 0.05;

      if (cursorLightRef.current) {
        cursorLightRef.current.style.transform = `translate3d(${currentX}px, ${currentY}px, 0)`;
      }
      rafId = requestAnimationFrame(updatePosition);
    };

    window.addEventListener('mousemove', handleMouseMove, { passive: true });
    rafId = requestAnimationFrame(updatePosition);

    return () => {
      window.removeEventListener('mousemove', handleMouseMove);
      cancelAnimationFrame(rafId);
    };
  }, []);

  // --------------------------------------------------------------------------
  // 2. Anthracite and Graphite Abstract Depth Field with Cyan Ambient Light Streaks
  //    Direct replica of the user's reference wallpaper with:
  //    - Volumetric vertical cyan light streaks swaying in atmospheric haze
  //    - Perspective brushed graphite floor plane with converging reflection rays
  //    - Bright specular reflection caustic spot where vertical beams meet floor
  //    - Subtle flowing undulating silk wave
  // --------------------------------------------------------------------------
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    const ctx = canvas.getContext('2d', { alpha: true });
    if (!ctx) return;

    let animId: number;
    let width = 0;
    let height = 0;
    let dpr = Math.min(window.devicePixelRatio || 1, 2);

    // Mouse parallax for 3D depth field sensation
    let mouseNormX = 0;
    let mouseNormY = 0;
    let curParallaxX = 0;
    let curParallaxY = 0;

    const handlePointerMove = (e: MouseEvent) => {
      mouseNormX = (e.clientX / window.innerWidth - 0.5) * 2;
      mouseNormY = (e.clientY / window.innerHeight - 0.5) * 2;
    };

    window.addEventListener('mousemove', handlePointerMove, { passive: true });

    const resizeCanvas = () => {
      width = window.innerWidth;
      height = window.innerHeight;
      dpr = Math.min(window.devicePixelRatio || 1, 2);
      canvas.width = width * dpr;
      canvas.height = height * dpr;
      canvas.style.width = `${width}px`;
      canvas.style.height = `${height}px`;
      ctx.scale(dpr, dpr);
    };

    resizeCanvas();
    window.addEventListener('resize', resizeCanvas);

    // ------------------------------------------------------------------------
    // Vertical Cyan Light Streaks (God Rays / Aurora Shafts)
    // ------------------------------------------------------------------------
    const streaks: VerticalStreak[] = [
      // Primary bright column (centered-right as in reference image)
      {
        xPercent: 0.62,
        width: 140,
        heightRatio: 1.0,
        speed: 0.00045,
        swayAmp: 25,
        pulseSpeed: 0.0008,
        pulseAmp: 0.18,
        baseOpacity: 0.55,
        phase: 0.4,
        colorHex: '32, 215, 255',
        subRaysCount: 8
      },
      // Secondary brilliant shaft directly adjacent
      {
        xPercent: 0.68,
        width: 90,
        heightRatio: 0.98,
        speed: 0.0006,
        swayAmp: 18,
        pulseSpeed: 0.0011,
        pulseAmp: 0.15,
        baseOpacity: 0.62,
        phase: 1.8,
        colorHex: '160, 240, 255',
        subRaysCount: 6
      },
      // Left mid-range diffuse beam
      {
        xPercent: 0.44,
        width: 110,
        heightRatio: 0.95,
        speed: 0.0004,
        swayAmp: 22,
        pulseSpeed: 0.0007,
        pulseAmp: 0.12,
        baseOpacity: 0.38,
        phase: 3.1,
        colorHex: '20, 190, 245',
        subRaysCount: 5
      },
      // Far left faint streak
      {
        xPercent: 0.28,
        width: 75,
        heightRatio: 0.92,
        speed: 0.0005,
        swayAmp: 15,
        pulseSpeed: 0.0009,
        pulseAmp: 0.1,
        baseOpacity: 0.25,
        phase: 4.5,
        colorHex: '14, 165, 233',
        subRaysCount: 4
      },
      // Far right accent streak
      {
        xPercent: 0.82,
        width: 85,
        heightRatio: 0.96,
        speed: 0.00055,
        swayAmp: 20,
        pulseSpeed: 0.00085,
        pulseAmp: 0.14,
        baseOpacity: 0.32,
        phase: 5.2,
        colorHex: '32, 205, 255',
        subRaysCount: 4
      },
      // Distant background broad haze curtain
      {
        xPercent: 0.58,
        width: 320,
        heightRatio: 1.0,
        speed: 0.00025,
        swayAmp: 30,
        pulseSpeed: 0.0005,
        pulseAmp: 0.08,
        baseOpacity: 0.18,
        phase: 0.9,
        colorHex: '10, 140, 200',
        subRaysCount: 10
      }
    ];

    // ------------------------------------------------------------------------
    // Perspective Floor Reflection Rays (Radiating from horizon)
    // ------------------------------------------------------------------------
    const floorRays: FloorRay[] = [];
    const floorRaysCount = 42;
    for (let i = 0; i < floorRaysCount; i++) {
      const norm = (i / (floorRaysCount - 1) - 0.5) * 2; // -1 to 1
      floorRays.push({
        angle: norm * 1.15, // spreading angle
        length: 0.8 + Math.random() * 0.4,
        opacity: 0.08 + Math.random() * 0.28,
        width: 1 + Math.random() * 2.2,
        color: Math.random() > 0.4 ? '32, 215, 255' : '160, 240, 255',
        shimmerSpeed: 0.0008 + Math.random() * 0.0014,
        phase: Math.random() * Math.PI * 2
      });
    }

    const startTime = performance.now();

    // ------------------------------------------------------------------------
    // Main Render Loop
    // ------------------------------------------------------------------------
    const render = (timeNow: number) => {
      if (document.hidden) {
        animId = requestAnimationFrame(render);
        return;
      }

      const elapsed = timeNow - startTime;
      ctx.clearRect(0, 0, width, height);

      // Smooth parallax easing
      curParallaxX += (mouseNormX * 25 - curParallaxX) * 0.04;
      curParallaxY += (mouseNormY * 12 - curParallaxY) * 0.04;

      // Horizon line position (~64% of viewport height)
      const horizonY = height * 0.64 + curParallaxY;
      const primaryCenter = width * 0.64 + curParallaxX;

      // ======================================================================
      // 1. Anthracite and Graphite Studio Atmosphere (Base Ambient Field)
      // ======================================================================
      const baseGrad = ctx.createRadialGradient(
        primaryCenter,
        horizonY * 0.8,
        50,
        primaryCenter,
        horizonY,
        Math.max(width, height) * 0.9
      );
      baseGrad.addColorStop(0, '#0B141E');
      baseGrad.addColorStop(0.35, '#070E16');
      baseGrad.addColorStop(0.75, '#04070B');
      baseGrad.addColorStop(1, '#020407');
      ctx.fillStyle = baseGrad;
      ctx.fillRect(0, 0, width, height);

      // ======================================================================
      // 2. Vertical Volumetric Light Streaks (Shafts descending to horizon)
      // ======================================================================
      ctx.globalCompositeOperation = 'screen';

      streaks.forEach((streak) => {
        const sway = Math.sin(elapsed * streak.speed + streak.phase) * streak.swayAmp;
        const pulse = Math.sin(elapsed * streak.pulseSpeed + streak.phase) * streak.pulseAmp;
        const curOpacity = Math.max(0.05, streak.baseOpacity + pulse);
        const streakX = width * streak.xPercent + sway + curParallaxX * 0.7;
        const bottomY = horizonY;
        const topY = 0;

        // Core volumetric light gradient for the main shaft
        const shaftGrad = ctx.createLinearGradient(0, topY, 0, bottomY);
        shaftGrad.addColorStop(0, `rgba(${streak.colorHex}, 0.0)`);
        shaftGrad.addColorStop(0.2, `rgba(${streak.colorHex}, ${curOpacity * 0.25})`);
        shaftGrad.addColorStop(0.65, `rgba(${streak.colorHex}, ${curOpacity * 0.6})`);
        shaftGrad.addColorStop(0.95, `rgba(${streak.colorHex}, ${curOpacity})`);
        shaftGrad.addColorStop(1, `rgba(255, 255, 255, ${curOpacity * 0.85})`);

        // Draw soft vertical beam
        ctx.fillStyle = shaftGrad;
        ctx.beginPath();
        ctx.rect(streakX - streak.width / 2, topY, streak.width, bottomY - topY);
        ctx.fill();

        // Draw fine internal light striations (striated light rays like in reference)
        for (let r = 0; r < streak.subRaysCount; r++) {
          const rayOffset = ((r / (streak.subRaysCount - 1)) - 0.5) * streak.width * 0.85;
          const rayX = streakX + rayOffset;
          const rayOpacity = curOpacity * (0.35 + 0.2 * Math.sin(elapsed * 0.002 + r));

          ctx.beginPath();
          ctx.moveTo(rayX, topY + 40);
          ctx.lineTo(rayX, bottomY);
          ctx.strokeStyle = `rgba(${streak.colorHex}, ${rayOpacity})`;
          ctx.lineWidth = 1 + (r % 3);
          ctx.shadowColor = '#20C7FF';
          ctx.shadowBlur = 8;
          ctx.stroke();
        }
      });

      // ======================================================================
      // 3. Horizon Focal Line & Atmospheric Caustic Bloom
      // ======================================================================
      // Soft wide horizon glow
      const horizonGlow = ctx.createRadialGradient(
        primaryCenter,
        horizonY,
        10,
        primaryCenter,
        horizonY,
        width * 0.55
      );
      horizonGlow.addColorStop(0, 'rgba(160, 240, 255, 0.75)');
      horizonGlow.addColorStop(0.2, 'rgba(32, 205, 255, 0.5)');
      horizonGlow.addColorStop(0.5, 'rgba(16, 201, 139, 0.18)');
      horizonGlow.addColorStop(1, 'transparent');

      ctx.fillStyle = horizonGlow;
      ctx.beginPath();
      ctx.ellipse(primaryCenter, horizonY, width * 0.5, 45, 0, 0, Math.PI * 2);
      ctx.fill();

      // Sharp luminous horizon contact line
      const lineGrad = ctx.createLinearGradient(primaryCenter - width * 0.4, 0, primaryCenter + width * 0.4, 0);
      lineGrad.addColorStop(0, 'transparent');
      lineGrad.addColorStop(0.3, 'rgba(32, 199, 255, 0.4)');
      lineGrad.addColorStop(0.5, 'rgba(255, 255, 255, 0.95)');
      lineGrad.addColorStop(0.7, 'rgba(32, 199, 255, 0.4)');
      lineGrad.addColorStop(1, 'transparent');

      ctx.beginPath();
      ctx.moveTo(primaryCenter - width * 0.45, horizonY);
      ctx.lineTo(primaryCenter + width * 0.45, horizonY);
      ctx.strokeStyle = lineGrad;
      ctx.lineWidth = 2;
      ctx.shadowColor = '#20C7FF';
      ctx.shadowBlur = 12;
      ctx.stroke();

      // ======================================================================
      // 4. Perspective Reflective Brushed Graphite Floor Plane
      // ======================================================================
      const floorHeight = height - horizonY;
      if (floorHeight > 0) {
        // Base dark graphite floor gradient
        const floorGrad = ctx.createLinearGradient(0, horizonY, 0, height);
        floorGrad.addColorStop(0, 'rgba(8, 16, 26, 0.92)');
        floorGrad.addColorStop(0.3, 'rgba(6, 12, 18, 0.85)');
        floorGrad.addColorStop(1, 'rgba(3, 6, 10, 0.96)');

        ctx.fillStyle = floorGrad;
        ctx.fillRect(0, horizonY, width, floorHeight);

        // Specular caustic floor reflection hotspot directly beneath vertical light shafts
        const floorReflection = ctx.createRadialGradient(
          primaryCenter,
          horizonY + 30,
          15,
          primaryCenter,
          horizonY + 70,
          width * 0.4
        );
        floorReflection.addColorStop(0, 'rgba(210, 248, 255, 0.7)');
        floorReflection.addColorStop(0.18, 'rgba(32, 205, 255, 0.45)');
        floorReflection.addColorStop(0.5, 'rgba(16, 201, 139, 0.12)');
        floorReflection.addColorStop(1, 'transparent');

        ctx.fillStyle = floorReflection;
        ctx.beginPath();
        ctx.ellipse(primaryCenter, horizonY + 50, width * 0.38, 70, 0, 0, Math.PI * 2);
        ctx.fill();

        // Converging perspective rays simulating brushed metal reflections (as in wallpaper)
        const vanishX = primaryCenter;
        const vanishY = horizonY - 40; // vanishing point slightly above horizon

        floorRays.forEach((ray) => {
          const shimmer = Math.sin(elapsed * ray.shimmerSpeed + ray.phase);
          const rayOpacity = Math.max(0.04, ray.opacity + shimmer * 0.12);

          // Calculate ray endpoint on bottom border
          const endX = vanishX + Math.tan(ray.angle) * (height - vanishY) * 1.5;
          const endY = height;

          // Only draw rays visible on screen
          if (endX >= -100 && endX <= width + 100) {
            const rayGrad = ctx.createLinearGradient(vanishX, horizonY, endX, endY);
            rayGrad.addColorStop(0, `rgba(${ray.color}, ${rayOpacity * 1.2})`);
            rayGrad.addColorStop(0.2, `rgba(${ray.color}, ${rayOpacity})`);
            rayGrad.addColorStop(0.65, `rgba(${ray.color}, ${rayOpacity * 0.45})`);
            rayGrad.addColorStop(1, 'transparent');

            ctx.beginPath();
            ctx.moveTo(vanishX, horizonY);
            ctx.lineTo(endX, endY);
            ctx.strokeStyle = rayGrad;
            ctx.lineWidth = ray.width;
            ctx.shadowColor = '#20C7FF';
            ctx.shadowBlur = 4;
            ctx.stroke();
          }
        });
      }

      // ======================================================================
      // 5. Subtle Luminous Cyan Silk Wave (Harmonious undulating flow)
      // ======================================================================
      const waveY = horizonY * 0.78;
      const step = 6;
      const pointsCount = Math.ceil(width / step) + 1;
      const wavePoints: { x: number; y: number }[] = [];

      for (let i = 0; i <= pointsCount; i++) {
        const x = i * step;
        const y =
          waveY +
          Math.sin(x * 0.0018 + elapsed * 0.00075) * 45 +
          Math.cos(x * 0.0035 + elapsed * 0.0011) * 20;
        wavePoints.push({ x, y });
      }

      // Draw sheer wave crest line
      ctx.beginPath();
      ctx.moveTo(wavePoints[0].x, wavePoints[0].y);
      for (let i = 1; i < wavePoints.length; i++) {
        ctx.lineTo(wavePoints[i].x, wavePoints[i].y);
      }
      ctx.strokeStyle = 'rgba(160, 240, 255, 0.45)';
      ctx.lineWidth = 1.5;
      ctx.shadowColor = '#20C7FF';
      ctx.shadowBlur = 10;
      ctx.stroke();

      // Reset composite operation and shadows
      ctx.shadowBlur = 0;
      ctx.globalCompositeOperation = 'source-over';

      animId = requestAnimationFrame(render);
    };

    animId = requestAnimationFrame(render);

    return () => {
      window.removeEventListener('resize', resizeCanvas);
      window.removeEventListener('mousemove', handlePointerMove);
      cancelAnimationFrame(animId);
    };
  }, []);

  return (
    <div className="ambient-background-container" aria-hidden="true">
      {/* Dynamic Anthracite Depth Field with Cyan Ambient Light Streaks Canvas */}
      <canvas ref={canvasRef} className="ambient-flowing-wave-canvas" />

      {/* Subtle Scientific Precision Grid */}
      <div className="ambient-technical-grid" />

      {/* Micro Studio Light Dust / Film Grain */}
      <div className="ambient-film-grain" />

      {/* Interactive Smooth Cursor Spotlight */}
      <div ref={cursorLightRef} className="ambient-cursor-light" />
    </div>
  );
};

export default AmbientBackground;
