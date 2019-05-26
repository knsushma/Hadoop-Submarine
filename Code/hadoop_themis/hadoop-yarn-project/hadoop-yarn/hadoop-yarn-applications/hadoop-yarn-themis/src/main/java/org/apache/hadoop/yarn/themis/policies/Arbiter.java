package org.apache.hadoop.yarn.themis.policies;

import java.util.List;

public interface Arbiter {
    public List<Event> runLogic(SysThemis system);
}