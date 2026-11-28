package com.edu360.api.t10;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named="EDU360_T10_JDBC_URL",matches=".+")
class TransportCapacityIT {
    @Test
    void concurrentAllocationsCannotExceedCapacityAndCancellationReleasesSeat() throws Exception {
        UUID tenant=UUID.randomUUID(), institution=UUID.randomUUID();
        UUID vehicle=UUID.randomUUID(), route=UUID.randomUUID(), stop=UUID.randomUUID();
        try(Connection c=connection()) {
            c.setAutoCommit(false);
            setScope(c,tenant,institution);
            try(PreparedStatement p=c.prepareStatement("INSERT INTO app.vehicle(id,tenant_id,institution_id,registration_number,capacity) VALUES (?,?,?,?,1)")) {
                p.setObject(1,vehicle);p.setObject(2,tenant);p.setObject(3,institution);p.setString(4,"T10-"+vehicle);p.executeUpdate();
            }
            try(PreparedStatement p=c.prepareStatement("INSERT INTO app.transport_route(id,tenant_id,institution_id,route_name,vehicle_id,vehicle_number,driver_name,total_capacity) VALUES (?,?,?,'Concurrency test',?,?,'Integration Driver',1)")) {
                p.setObject(1,route);p.setObject(2,tenant);p.setObject(3,institution);p.setObject(4,vehicle);p.setString(5,"T10-"+vehicle);p.executeUpdate();
            }
            try(PreparedStatement p=c.prepareStatement("INSERT INTO app.route_stop(id,tenant_id,institution_id,route_id,stop_name,stop_order) VALUES (?,?,?,?,'Test stop',1)")) {
                p.setObject(1,stop);p.setObject(2,tenant);p.setObject(3,institution);p.setObject(4,route);p.executeUpdate();
            }
            c.commit();
        }

        UUID passengerA=UUID.randomUUID(), passengerB=UUID.randomUUID();
        CountDownLatch ready=new CountDownLatch(2), start=new CountDownLatch(1);
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> a=pool.submit(() -> attemptAllocation(tenant,institution,route,stop,passengerA,ready,start));
            Future<Boolean> b=pool.submit(() -> attemptAllocation(tenant,institution,route,stop,passengerB,ready,start));
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        } finally {
            pool.shutdownNow();
        }

        try(Connection c=connection()) {
            c.setAutoCommit(false);setScope(c,tenant,institution);
            try(PreparedStatement p=c.prepareStatement("SELECT allocated_seats,(SELECT count(*) FROM app.transport_allocation WHERE route_id=? AND status='ACTIVE') FROM app.transport_route WHERE id=?")) {
                p.setObject(1,route);p.setObject(2,route);
                try(ResultSet rs=p.executeQuery()){assertThat(rs.next()).isTrue();assertThat(rs.getInt(1)).isEqualTo(1);assertThat(rs.getInt(2)).isEqualTo(1);}
            }
            try(PreparedStatement p=c.prepareStatement("UPDATE app.transport_route SET active=false WHERE id=?")){p.setObject(1,route);p.executeUpdate();}
            try(PreparedStatement p=c.prepareStatement("UPDATE app.transport_allocation SET status='CANCELLED' WHERE route_id=? AND status='ACTIVE'")){p.setObject(1,route);p.executeUpdate();}
            try(PreparedStatement p=c.prepareStatement("SELECT allocated_seats FROM app.transport_route WHERE id=?")){p.setObject(1,route);try(ResultSet rs=p.executeQuery()){assertThat(rs.next()).isTrue();assertThat(rs.getInt(1)).isZero();}}
            c.commit();
        }
        assertThat(attemptAllocation(tenant,institution,route,stop,UUID.randomUUID(),new CountDownLatch(0),new CountDownLatch(0))).isFalse();
    }

    private boolean attemptAllocation(UUID tenant,UUID institution,UUID route,UUID stop,UUID passenger,
                                      CountDownLatch ready,CountDownLatch start) throws Exception {
        try(Connection c=connection()) {
            c.setAutoCommit(false);setScope(c,tenant,institution);
            ready.countDown();
            if(!start.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("allocation barrier timed out");
            try(PreparedStatement p=c.prepareStatement("INSERT INTO app.transport_allocation(id,tenant_id,institution_id,route_id,stop_id,passenger_user_id,status) VALUES (?,?,?,?,?,?,'ACTIVE')")) {
                p.setObject(1,UUID.randomUUID());p.setObject(2,tenant);p.setObject(3,institution);p.setObject(4,route);p.setObject(5,stop);p.setObject(6,passenger);p.executeUpdate();
            }
            c.commit();
            return true;
        } catch(SQLException ex) {
            if("23514".equals(ex.getSQLState())) return false;
            throw ex;
        }
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(System.getenv("EDU360_T10_JDBC_URL"),System.getenv("EDU360_T10_JDBC_USER"),System.getenv("EDU360_T10_JDBC_PASSWORD"));
    }

    private void setScope(Connection c,UUID tenant,UUID institution) throws SQLException {
        try(PreparedStatement p=c.prepareStatement("SELECT set_config('app.tenant_id',?,true),set_config('app.institution_scope','LIST',true),set_config('app.institution_ids',?,true)")) {
            p.setString(1,tenant.toString());p.setString(2,institution.toString());p.execute();
        }
    }
}
