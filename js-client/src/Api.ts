/*
 * Copyright (c) 2016-2017 Flux Capacitor.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

export class Message {
    private data;

    constructor(type: string, payload: any, revision?: Number, payloadSerialized?: boolean) {
        this.data = {
            type: type,
            value: payloadSerialized ? payload : window.btoa(JSON.stringify(payload)),
            revision: revision || 0
        };
    }

    type(): string {
        return this.data.type;
    }

    payload(): any {
        return JSON.parse(window.atob(this.data.value));
    }

    revision(): Number {
        return this.data.revision;
    }
}

export class MessageBatch {
    messages: Message[];
    segment: [number, number];
    lastIndex: any;

    static deserialize(object : any) : MessageBatch {
        const result = new MessageBatch();
        result.segment = object.segment;
        result.lastIndex = object.lastIndex;
        result.messages = [];
        for (let msg of object.messages) {
            result.messages.push(new Message(msg.data.type, msg.data.value, msg.data.revision, true));
        }
        return result;
    }
}

export enum MessageType {
    command, event, query, result, schedule, usage
}

export abstract class Request {
    requestId : number = Request.lastId++;

    private static lastId : number = 0;
}

export abstract class RequestResult {
    requestId : number;
}

export class Append {
    '@type' = 'append';
    messages: Message[];

    constructor(messages: Message[]) {
        this.messages = messages;
    }
}

export class Read extends Request {
    '@type' = 'read';
    processor: string;
    channel: number;
    maxSize: number;
    maxTimeout: number;

    constructor(consumer: string, maxSize?: number, maxTimeout?: number, channel?: number) {
        super();
        this.processor = consumer;
        this.channel = channel || 0;
        this.maxSize = maxSize || 1024;
        this.maxTimeout = maxTimeout || 60000;
    }
}

export class ReadResult extends RequestResult {
    messageBatch : MessageBatch;
}

export class StorePosition {
    '@type' = 'storePosition';
    processor : string;
    segment : [number, number];
    lastIndex : any;

    constructor(consumer: string, segment: [number, number], lastIndex: any) {
        this.processor = consumer;
        this.segment = segment;
        this.lastIndex = lastIndex;
    }
}

