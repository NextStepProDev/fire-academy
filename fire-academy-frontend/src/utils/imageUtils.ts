// Input-side guard only (protects the browser from decoding absurd files) — after client-side
// compression the file sent to the server is small; the server's own 10 MB limit stays as defense in depth.
const MAX_FILE_SIZE = 50 * 1024 * 1024 // 50 MB
const MAX_DIMENSION = 1920
const OUTPUT_QUALITY = 0.85
const COMPRESS_THRESHOLD = 2 * 1024 * 1024 // 2 MB

export function validateImageFile(file: File): string | null {
  if (file.size > MAX_FILE_SIZE) {
    const sizeMB = (file.size / (1024 * 1024)).toFixed(1)
    return `Plik jest za duży (${sizeMB} MB). Maksymalny rozmiar to 50 MB.`
  }
  const allowed = ['image/jpeg', 'image/png', 'image/webp']
  if (!allowed.includes(file.type)) {
    return 'Niedozwolony format pliku. Dozwolone: JPG, PNG, WebP.'
  }
  return null
}

// WebP output shrinks photographic PNGs too (PNG ignores the quality parameter) and keeps alpha
// (logos/badges). Old Safari can't encode WebP — detect once and fall back to JPEG there.
let webpSupported: boolean | null = null
function canEncodeWebp(): boolean {
  if (webpSupported === null) {
    webpSupported = document
      .createElement('canvas')
      .toDataURL('image/webp')
      .startsWith('data:image/webp')
  }
  return webpSupported
}

export async function compressImage(file: File): Promise<File> {
  if (file.size <= COMPRESS_THRESHOLD && !(await exceedsDimension(file))) {
    return file
  }

  return new Promise((resolve, reject) => {
    const img = new Image()
    img.onload = () => {
      URL.revokeObjectURL(img.src)

      let { width, height } = img
      if (width > MAX_DIMENSION || height > MAX_DIMENSION) {
        const ratio = Math.min(MAX_DIMENSION / width, MAX_DIMENSION / height)
        width = Math.round(width * ratio)
        height = Math.round(height * ratio)
      }

      const canvas = document.createElement('canvas')
      canvas.width = width
      canvas.height = height
      const ctx = canvas.getContext('2d')!
      ctx.drawImage(img, 0, 0, width, height)

      const mimeType = canEncodeWebp() ? 'image/webp' : 'image/jpeg'
      canvas.toBlob(
        blob => {
          // Keep the smaller of the two — never "compress" a file into a bigger one.
          if (!blob || blob.size >= file.size) {
            resolve(file)
            return
          }
          const ext = mimeType === 'image/webp' ? '.webp' : '.jpg'
          const name = file.name.replace(/\.[^.]+$/, ext)
          resolve(new File([blob], name, { type: mimeType }))
        },
        mimeType,
        OUTPUT_QUALITY,
      )
    }
    img.onerror = () => reject(new Error('Nie udało się wczytać obrazu'))
    img.src = URL.createObjectURL(file)
  })
}

function exceedsDimension(file: File): Promise<boolean> {
  return new Promise(resolve => {
    const img = new Image()
    img.onload = () => {
      URL.revokeObjectURL(img.src)
      resolve(img.width > MAX_DIMENSION || img.height > MAX_DIMENSION)
    }
    img.onerror = () => resolve(false)
    img.src = URL.createObjectURL(file)
  })
}
