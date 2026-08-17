package com.processpuzzle.basestate.adapter.inbound.dto;

import java.util.List;

public record StateMachineYamlDocument(List<StateMachineYamlEntry> stateMachines) {
}
