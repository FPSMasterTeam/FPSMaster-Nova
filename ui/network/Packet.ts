import { PacketBuffer } from './PacketBuffer';

export enum PacketDirection {
  CLIENT_TO_SERVER,
  SERVER_TO_CLIENT,
  BIDIRECTIONAL
}

export interface Packet {
  packetId: number;
  
  write(buffer: PacketBuffer): void;
  read(buffer: PacketBuffer): void;
}
