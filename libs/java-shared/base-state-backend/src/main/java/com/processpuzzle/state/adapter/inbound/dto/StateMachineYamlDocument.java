package com.processpuzzle.state.adapter.inbound.dto;

import java.util.List;

public record StateMachineYamlDocument(List<StateMachineYamlEntry> stateMachines) {
}
