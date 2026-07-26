import { Packet } from './Packet';
import { PacketSerializer } from './PacketSerializer';
import { PacketRegistry } from './PacketRegistry';
import { PacketProcessor } from './PacketProcessor';

export class WebSocketClient {
  private ws: WebSocket | null = null;
  private url: string;
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

  connect() {
    if (this.ws && (this.ws.readyState === WebSocket.OPEN || this.ws.readyState === WebSocket.CONNECTING)) {
        return;
    }

    console.info(`[WS] Connecting to ${this.url}`);
    try {
        this.ws = new WebSocket(this.url);
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
