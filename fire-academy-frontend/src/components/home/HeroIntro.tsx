import { useState, useEffect } from 'react'

const SPARRING_BG = '/images/posters/przemo-mma-zwyciestwo.jpeg'

export function HeroIntro({ onComplete }: { onComplete: () => void }) {
  const [phase, setPhase] = useState<'enter' | 'clash' | 'exit'>('enter')

  useEffect(() => {
    const clashTimer = setTimeout(() => setPhase('clash'), 80)
    const exitTimer = setTimeout(() => setPhase('exit'), 2300)
    return () => {
      clearTimeout(clashTimer)
      clearTimeout(exitTimer)
    }
  }, [])

  // Finish after the fade-out animation (700 ms) — regardless of whether the exit phase
  // happened naturally or via keyboard skip.
  useEffect(() => {
    if (phase !== 'exit') return
    const doneTimer = setTimeout(onComplete, 700)
    return () => clearTimeout(doneTimer)
  }, [phase, onComplete])

  // Skip the intro with a key (Escape / Enter / Space).
  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape' || e.key === 'Enter' || e.key === ' ') {
        e.preventDefault()
        setPhase('exit')
      }
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [])

  const met = phase !== 'enter'

  return (
    <div
      onClick={() => setPhase('exit')}
      className={`fixed inset-0 z-50 flex items-center justify-center overflow-hidden transition-opacity duration-700 ${
        phase === 'exit' ? 'opacity-0 pointer-events-none' : 'opacity-100'
      }`}
    >
      <div
        className="absolute inset-0 bg-cover bg-[center_20%] scale-125"
        style={{ backgroundImage: `url(${SPARRING_BG})` }}
      />
      <div className="absolute inset-0 bg-black/55" />

      {/* Fire burst at the moment the logos clash */}
      {met && (
        <div
          className="intro-burst pointer-events-none absolute left-1/2 top-1/2 h-[60vmin] w-[60vmin] rounded-full"
          style={{
            background:
              'radial-gradient(circle, rgba(249,115,22,0.95) 0%, rgba(249,115,22,0.35) 35%, rgba(249,115,22,0) 65%)',
          }}
        />
      )}

      <div className="relative z-10 flex flex-col items-center gap-8">
        <div className={`flex items-center justify-center gap-5 md:gap-8 ${met ? 'intro-glow' : ''}`}>
          {/* ACADEMY FIRE — flies in from the left */}
          <img
            src="/images/logo/logo-academy-fire-aligned.png"
            alt="Fire Academy"
            className={`h-32 w-auto md:h-44 transition-all duration-[700ms] ease-out ${
              met ? 'translate-x-0 opacity-100' : '-translate-x-[60vw] opacity-0'
            }`}
          />

          {/* Separator — pops in when they meet */}
          <span
            className={`h-20 w-[3px] origin-center rounded-full bg-white/45 transition-all duration-300 ease-out md:h-28 ${
              met ? 'scale-y-100 opacity-100 delay-[650ms]' : 'scale-y-0 opacity-0'
            }`}
          />

          {/* FIRE CAMP — flies in from the right */}
          <img
            src="/images/logo/logo-fire-camp-aligned.png"
            alt="Fire Camp"
            className={`h-32 w-auto md:h-44 transition-all duration-[700ms] ease-out ${
              met ? 'translate-x-0 opacity-100' : 'translate-x-[60vw] opacity-0'
            }`}
          />
        </div>

        <p
          className={`text-lg tracking-[0.5em] text-primary-300/90 uppercase md:text-xl transition-all duration-700 ease-out ${
            met ? 'translate-y-0 opacity-100 delay-[1100ms]' : 'translate-y-4 opacity-0'
          }`}
        >
          Trenuj z ogniem
        </p>
      </div>

      <div
        className={`absolute bottom-0 left-0 h-1 bg-primary-500 transition-all ease-linear ${
          phase === 'enter' ? 'w-0 duration-0' : 'w-full duration-[2300ms]'
        }`}
      />
    </div>
  )
}
