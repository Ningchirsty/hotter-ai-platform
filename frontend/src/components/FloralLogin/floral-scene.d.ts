export interface FloralSceneController {
  available: boolean;
  readonly playing: boolean;
  setPlaying(value: boolean): void;
  destroy(): void;
}
export interface FloralSceneOptions {
  playing?: boolean;
  onFallback?: () => void;
  onError?: (error: unknown) => void;
}
export function createFloralScene(root: HTMLElement, canvas: HTMLCanvasElement, options?: FloralSceneOptions): FloralSceneController;
