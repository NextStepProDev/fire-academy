import { useState, useEffect } from 'react'

const SPARRING_BG =
  'https://images.unsplash.com/photo-1525680996651-0222228be6f0?w=1920&q=80'

export function HeroIntro({ onComplete }: { onComplete: () => void }) {
  const [phase, setPhase] = useState<'enter' | 'hold' | 'exit'>('enter')

  useEffect(() => {
    const enterTimer = setTimeout(() => setPhase('hold'), 100)
    const exitTimer = setTimeout(() => setPhase('exit'), 3100)
    const doneTimer = setTimeout(onComplete, 3800)
    return () => {
      clearTimeout(enterTimer)
      clearTimeout(exitTimer)
      clearTimeout(doneTimer)
    }
  }, [onComplete])

  return (
    <div
      className={`fixed inset-0 z-50 flex items-center justify-center transition-opacity duration-700 ${
        phase === 'exit' ? 'opacity-0' : 'opacity-100'
      }`}
    >
      <div
        className="absolute inset-0 bg-cover bg-[60%_30%] md:bg-center scale-110"
        style={{ backgroundImage: `url(${SPARRING_BG})` }}
      />

      <div className="absolute inset-0 bg-black/45" />

      <div className="relative z-10 flex flex-col items-center gap-6 md:flex-row md:gap-10">
        <img
          src="/images/logo/logo-white.png"
          alt="Fire Academy"
          className={`h-28 w-auto drop-shadow-[0_0_30px_rgba(249,115,22,0.6)] md:h-40 transition-all duration-1000 ease-out ${
            phase === 'enter'
              ? '-translate-x-24 opacity-0 scale-75'
              : 'translate-x-0 opacity-100 scale-100'
          }`}
        />

        <div className="flex flex-col items-center md:items-start">
          <h1
            className={`text-5xl font-black tracking-[0.25em] text-white uppercase md:text-7xl lg:text-8xl transition-all duration-1000 ease-out delay-200 ${
              phase === 'enter'
                ? 'translate-x-24 opacity-0'
                : 'translate-x-0 opacity-100'
            }`}
          >
            <span className="text-primary-400">FIRE</span>
          </h1>
          <h1
            className={`text-5xl font-black tracking-[0.25em] text-white uppercase md:text-7xl lg:text-8xl transition-all duration-1000 ease-out delay-400 ${
              phase === 'enter'
                ? 'translate-x-24 opacity-0'
                : 'translate-x-0 opacity-100'
            }`}
          >
            ACADEMY
          </h1>
          <p
            className={`mt-3 text-lg tracking-[0.5em] text-primary-300/80 uppercase md:text-xl transition-all duration-700 ease-out delay-700 ${
              phase === 'enter' ? 'opacity-0 translate-y-4' : 'opacity-100 translate-y-0'
            }`}
          >
            Trenuj z ogniem
          </p>
        </div>
      </div>

      <div
        className={`absolute bottom-0 left-0 h-1 bg-primary-500 transition-all ease-linear ${
          phase === 'enter' ? 'w-0 duration-0' : 'w-full duration-3500'
        }`}
      />
    </div>
  )
}
