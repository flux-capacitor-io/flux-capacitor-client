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

import {Append, Message, MessageBatch, Read, ReadResult, StorePosition} from './Api';
import {WebSocketService} from './WebSocketService';

export interface ConsumerService {
    read(consumer : string, maxSize : number, maxTimeout : number): Promise<MessageBatch>;

    storePosition(consumer : string, segment : [number, number], lastIndex : any) : void;
}

export interface ProducerService {
    publish(messages : Message[]) : void;
}

export class WebSocketConsumerService extends WebSocketService implements ConsumerService {
    read(consumer: string, maxSize: number, maxTimeout: number): Promise<MessageBatch> {
        const result : Promise<ReadResult> = this.sendRequest(new Read(consumer, maxSize, maxTimeout, 0));
        return result.then(success => MessageBatch.deserialize(success.messageBatch));
    }

    storePosition(consumer: string, segment: [number, number], lastIndex: any) : void {
        this.send(new StorePosition(consumer, segment, lastIndex));
    }
}

export class WebSocketProducerService extends WebSocketService implements ProducerService {
    publish(messages: Message[]) : void {
        this.send(new Append(messages));
    }
}

export async function startTracking(consumerName: string, consumer: Function, service: ConsumerService) {
    let stopped = false;
    while (!stopped) {
        const batch = await service.read(consumerName, 64, 60000);
        batch.messages.map(msg => {
            return {
                type: msg.type(),
                payload: msg.payload(),
                revision: msg.revision()
            }
        }).forEach(msg => consumer(msg));
        if (batch.lastIndex) {
            service.storePosition(consumerName, batch.segment, batch.lastIndex);
        }
    }
}