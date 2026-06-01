import { describe, it, expect } from 'vitest'
import { validateImageFile } from './imageUtils'

function createFile(name: string, size: number, type: string): File {
  const buffer = new ArrayBuffer(size)
  return new File([buffer], name, { type })
}

describe('validateImageFile', () => {
  it('should accept valid JPEG under 10MB', () => {
    const file = createFile('photo.jpg', 5 * 1024 * 1024, 'image/jpeg')
    expect(validateImageFile(file)).toBeNull()
  })

  it('should accept valid PNG under 10MB', () => {
    const file = createFile('image.png', 1 * 1024 * 1024, 'image/png')
    expect(validateImageFile(file)).toBeNull()
  })

  it('should accept valid WebP under 10MB', () => {
    const file = createFile('image.webp', 2 * 1024 * 1024, 'image/webp')
    expect(validateImageFile(file)).toBeNull()
  })

  it('should reject file exceeding 10MB', () => {
    const file = createFile('huge.jpg', 11 * 1024 * 1024, 'image/jpeg')
    const error = validateImageFile(file)
    expect(error).not.toBeNull()
    expect(error).toContain('10 MB')
  })

  it('should reject file exactly at boundary', () => {
    const file = createFile('edge.jpg', 10 * 1024 * 1024 + 1, 'image/jpeg')
    expect(validateImageFile(file)).not.toBeNull()
  })

  it('should accept file exactly at 10MB', () => {
    const file = createFile('exact.jpg', 10 * 1024 * 1024, 'image/jpeg')
    expect(validateImageFile(file)).toBeNull()
  })

  it('should reject GIF format', () => {
    const file = createFile('anim.gif', 500 * 1024, 'image/gif')
    const error = validateImageFile(file)
    expect(error).not.toBeNull()
    expect(error).toContain('JPG')
  })

  it('should reject PDF format', () => {
    const file = createFile('doc.pdf', 500 * 1024, 'application/pdf')
    expect(validateImageFile(file)).not.toBeNull()
  })

  it('should reject SVG format', () => {
    const file = createFile('icon.svg', 10 * 1024, 'image/svg+xml')
    expect(validateImageFile(file)).not.toBeNull()
  })

  it('should include file size in error message', () => {
    const file = createFile('big.jpg', 15 * 1024 * 1024, 'image/jpeg')
    const error = validateImageFile(file)
    expect(error).toContain('15.0')
  })
})
