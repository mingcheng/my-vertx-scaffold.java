package com.gracecode.scaffold.verticle;

import com.gracecode.scaffold.Greet;
import com.gracecode.scaffold.GreetingServiceGrpc;
import com.gracecode.scaffold.Person;
import com.gracecode.scaffold.service.impl.GrpcServiceImpl;
import io.grpc.ManagedChannel;
import io.reactivex.Single;
import io.vertx.core.Future;
import io.vertx.grpc.VertxChannelBuilder;
import io.vertx.reactivex.core.impl.AsyncResultSingle;

import java.util.Random;

/**
 * @author mingcheng
 */
public class ConsumerVerticle extends BaseVerticle {

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        super.start(startFuture);

        consulClient.rxCatalogServiceNodes(GrpcServiceImpl.RPC_SERVER_NAME)
                .flatMap(result -> {
                    if (result.getList().isEmpty()) {
                        return Single.error(
                                new Throwable(String.format("Not found %s address on Consul.",
                                        GrpcServiceImpl.RPC_SERVER_NAME))
                        );
                    } else {
                        // Only get the first record.
                        return Single.just(result.getList().get(0));
                    }
                })
                .subscribe(service -> {
                    logger.info(String.format(
                            "Get %s Service from Consul with address/port %s:%d",
                            GrpcServiceImpl.RPC_SERVER_NAME, service.getAddress(), service.getPort()));

                    // Greet to rpc server for test every 2sec.
                    vertx.setPeriodic(2000, handler -> {
                        greetFromRpcServer(service.getAddress(), service.getPort());
                    });
                }, logger::fatal);
    }


    /**
     * 发送和返回 RPC 消息
     *
     * @param host String
     * @param port int
     */
    private void greetFromRpcServer(String host, int port) {
        new AsyncResultSingle<Greet>(handler -> {
            ManagedChannel rpcChannel = VertxChannelBuilder
                    .forAddress(getVertx(), host, port)
                    .usePlaintext(true)
                    .build();

            GreetingServiceGrpc.GreetingServiceVertxStub stub = GreetingServiceGrpc.newVertxStub(rpcChannel);

            // A person with random age
            Person person = Person.newBuilder()
                    .setName(getClass().getSimpleName())
                    .setAge(new Random().nextInt(100))
                    .build();

            // Greet from rpc server
            stub.greet(person, handler);
        }).subscribe(p -> {
            logger.info(p.getMessage());
        }, error -> {
            logger.fatal(error);
        });
    }
}