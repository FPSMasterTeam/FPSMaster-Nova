import { Packet } from './Packet';
import { PacketSerializer } from './PacketSerializer';
import { PacketRegistry } from './PacketRegistry';
import { PacketProcessor } from './PacketProcessor';

export class WebSocketClient {
  private ws: WebSocket | null = null;
  private url: string;
  private resolvedUrl: string | null = null;
  private reconnectInterval: number = 3000;

  // Callbacks for UI status
  public onStatusChange: (status: string) => void = () => {};

  // 127.0.0.1 rather than 'localhost': the client binds the IPv4 loopback only, and where the OS answers
  // 'localhost' with ::1 first every connect wastes a refused attempt before Chromium falls back.
  constructor(url: string = 'ws://127.0.0.1:4399/websocket') {
    this.url = url;
    // Expose globally for debugging
    (window as any).fps_ws_client = this;
  }

  // The WS server auto-falls-back off its default port (4399) when it is busy, so the port is not
  // fixed. Ask the mod's HTTP server (relative /api/ws-port, proxied to it in dev) for the actual
  // port before connecting; on any failure fall back to the hardcoded default. Cached after the
  // first successful lookup so reconnects don't re-fetch.
  private async resolveUrl(): Promise<string> {
    if (this.resolvedUrl) return this.resolvedUrl;
    try {
      const res = await fetch('/api/ws-port', { cache: 'no-store' });
      if (res.ok) {
        const port = Number((await res.json())?.port);
        if (Number.isFinite(port) && port > 0) {
          this.resolvedUrl = `ws://127.0.0.1:${port}/websocket`;
          return this.resolvedUrl;
        }
      }
    } catch (e) {
      console.warn('[WS] Failed to resolve ws port, falling back to default', e);
    }
    return this.url;
  }

  async connect() {
    if (this.ws && (this.ws.readyState === WebSocket.OPEN || this.ws.readyState === WebSocket.CONNECTING)) {
        return;
    }

    const url = await this.resolveUrl();
    console.info(`[WS] Connecting to ${url}`);
    try {
        this.ws = new WebSocket(url);
    } catch (e) {
        console.error("[WS] Connection creation failed", e);
        this.scheduleReconnect();
        return;
    }

    this.ws.onopen = () => {
      console.info('[WS] Connected');
      this.onStatusChange('open');
    };

    this.ws.onclose = (e) => {
      console.warn(`[WS] Closed: ${e.code} ${e.reason}`);
      this.onStatusChange(`close:${e.code}`);
      this.scheduleReconnect();
    };

    this.ws.onerror = (e) => {
      console.error('[WS] Error', e);
      this.onStatusChange('error');
    };

    this.ws.onmessage = (e) => {
      // console.debug(`[WS] Message: ${e.data.length} bytes`);
      this.handleMessage(e.data);
    };
  }

  private scheduleReconnect() {
      setTimeout(() => this.connect(), this.reconnectInterval);
  }

  private handleMessage(data: string) {
    try {
        const packet = PacketSerializer.deserialize(data, (id) => PacketRegistry.create(id));
        if (packet) {
        PacketProcessor.process(packet);
        }
    } catch (e) {
        console.error("[WS] Failed to handle message", e);
    }
  }

  send(packet: Packet) {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      const data = PacketSerializer.serialize(packet);
      this.ws.send(data);
    } else {
      console.warn('[WS] Cannot send packet, not connected');
    }
  }
}

export const NetworkManager = new WebSocketClient();
