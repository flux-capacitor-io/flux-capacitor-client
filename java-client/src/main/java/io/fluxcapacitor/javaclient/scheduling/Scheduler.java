package io.fluxcapacitor.javaclient.scheduling;

public interface Scheduler {

    void schedule(Schedule message);

    void cancelSchedule(String scheduleId);

}
