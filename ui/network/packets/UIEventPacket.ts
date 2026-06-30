import { Packet } from '../Packet';
import { PacketBuffer } from '../PacketBuffer';

// UI -> backend generic action channel (matches backend UIEventPacket, id 8).
// Used e.g. to open the in-game HUD editor: new UIEventPacket('open-hud-editor').
export class UIEventPacket implements Packet {
  packetId = 8;
  eventType: string;
  eventData: string | null;

  constructor(eventType: string = '', eventData: string | null = null) {
    this.eventType = eventType;
    this.eventData = eventData;
  }

  write(buffer: PacketBuffer): void {
    buffer.writeString(this.eventType);
    buffer.writeString(this.eventData);
  }

  read(buffer: PacketBuffer): void {
    this.eventType = buffer.readString() || '';
    this.eventData = buffer.readString();
  }
}
