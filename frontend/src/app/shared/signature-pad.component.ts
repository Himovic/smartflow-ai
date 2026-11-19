import { AfterViewInit, Component, ElementRef, output, viewChild } from '@angular/core';
import { TrPipe } from './tr.pipe';

@Component({
  selector: 'sf-signature',
  imports: [TrPipe],
  template: `<div class="sig"><canvas #c width="520" height="160"></canvas>
    <button type="button" class="btn sm ghost clear" (click)="clear()"><span class="icon">ink_eraser</span>{{ 'common.clear' | tr }}</button></div>`,
  styles: [`.sig { position: relative; border: 1px dashed var(--border-strong); border-radius: 10px; background: #fff; }
    canvas { width: 100%; height: 160px; touch-action: none; display: block; } .clear { position: absolute; top: 6px; inset-inline-end: 6px; }`],
})
export class SignaturePadComponent implements AfterViewInit {
  readonly signed = output<string | null>();
  private canvas = viewChild.required<ElementRef<HTMLCanvasElement>>('c');
  private drawing = false;
  private dirty = false;

  ngAfterViewInit(): void {
    const c = this.canvas().nativeElement;
    const ctx = c.getContext('2d')!;
    ctx.lineWidth = 2.2;
    ctx.lineCap = 'round';
    ctx.strokeStyle = '#0b1f3a';
    const pos = (e: PointerEvent) => {
      const r = c.getBoundingClientRect();
      return [((e.clientX - r.left) / r.width) * c.width, ((e.clientY - r.top) / r.height) * c.height];
    };
    c.addEventListener('pointerdown', (e) => { this.drawing = true; const [x, y] = pos(e); ctx.beginPath(); ctx.moveTo(x, y); });
    c.addEventListener('pointermove', (e) => { if (!this.drawing) return; const [x, y] = pos(e); ctx.lineTo(x, y); ctx.stroke(); this.dirty = true; });
    const end = () => { if (this.drawing && this.dirty) this.signed.emit(c.toDataURL('image/png')); this.drawing = false; };
    c.addEventListener('pointerup', end);
    c.addEventListener('pointerleave', end);
  }

  clear(): void {
    const c = this.canvas().nativeElement;
    c.getContext('2d')!.clearRect(0, 0, c.width, c.height);
    this.dirty = false;
    this.signed.emit(null);
  }
}
