import { Packet } from '../Packet';
import { PacketBuffer } from '../PacketBuffer';

export class AccountStatusRequestPacket implements Packet {
  packetId = 18;

  write(_buffer: PacketBuffer): void {
    // No payload
  }

  read(_buffer: PacketBuffer): void {
    // No payload
  }
}

export class AccountStatusPacket implements Packet {
  packetId = 19;
  success = true;
  loggedIn = false;
  username: string | null = null;
  displayName: string | null = null;
  level = 0;
  message = '';

  write(buffer: PacketBuffer): void {
    buffer.writeBoolean(this.success);
    buffer.writeBoolean(this.loggedIn);
    buffer.writeString(this.username);
    buffer.writeString(this.displayName);
    buffer.writeInt(this.level);
    buffer.writeString(this.message);
  }

  read(buffer: PacketBuffer): void {
    this.success = buffer.readBoolean();
    this.loggedIn = buffer.readBoolean();
    this.username = buffer.readString();
    this.displayName = buffer.readString();
    this.level = buffer.readInt();
    this.message = buffer.readString() || '';
  }
}

export class AccountLoginPacket implements Packet {
  packetId = 20;

  constructor(
    public usernameOrEmail: string = '',
    public password: string = '',
  ) {}

  write(buffer: PacketBuffer): void {
    buffer.writeString(this.usernameOrEmail);
    buffer.writeString(this.password);
  }

  read(buffer: PacketBuffer): void {
    this.usernameOrEmail = buffer.readString() || '';
    this.password = buffer.readString() || '';
  }
}

export class AccountLogoutPacket implements Packet {
  packetId = 21;

  write(_buffer: PacketBuffer): void {
    // No payload
  }

  read(_buffer: PacketBuffer): void {
    // No payload
  }
}
