package com._gods.simplelms.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com._gods.simplelms.model.Group;
import com._gods.simplelms.service.GroupService;
import com._gods.simplelms.web.dto.GroupMembershipResponse;
import com._gods.simplelms.web.dto.GroupRequest;
import com._gods.simplelms.web.dto.GroupResponse;

@RestController
public class GroupController {

  private final GroupService groupService;

  public GroupController(GroupService groupService) {
    this.groupService = groupService;
  }

  @PostMapping("/api/courses/{courseId}/groups")
  public ResponseEntity<GroupResponse> createGroup(@PathVariable Long courseId,
      @RequestBody GroupRequest request, Authentication authentication) {
    Group group = groupService.createGroup(courseId, request.name(), authentication.getName());
    return ResponseEntity.status(HttpStatus.CREATED).body(GroupResponse.from(group));
  }

  @DeleteMapping("/api/groups/{groupId}")
  public ResponseEntity<Void> deleteGroup(@PathVariable Long groupId,
      Authentication authentication) {
    groupService.deleteGroup(groupId, authentication.getName());
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/api/courses/{courseId}/groups")
  public List<GroupResponse> getGroupsByCourse(@PathVariable Long courseId,
      Authentication authentication) {
    return groupService.getGroupsByCourse(courseId, authentication.getName()).stream()
        .map(GroupResponse::from)
        .toList();
  }

  @GetMapping("/api/courses/{courseId}/groups/mine")
  public GroupResponse getOwnGroup(@PathVariable Long courseId, Authentication authentication) {
    return GroupResponse.from(groupService.getOwnGroup(courseId, authentication.getName()));
  }

  @GetMapping("/api/groups/{groupId}/members")
  public List<GroupMembershipResponse> getMembers(@PathVariable Long groupId,
      Authentication authentication) {
    return groupService.getMembers(groupId, authentication.getName()).stream()
        .map(GroupMembershipResponse::from)
        .toList();
  }

  @PostMapping("/api/groups/{groupId}/members/{email}")
  public ResponseEntity<GroupMembershipResponse> addMember(@PathVariable Long groupId,
      @PathVariable String email, Authentication authentication) {
    var membership = groupService.addMember(groupId, email, authentication.getName());
    return ResponseEntity.status(HttpStatus.CREATED).body(GroupMembershipResponse.from(membership));
  }

  @DeleteMapping("/api/groups/{groupId}/members/{email}")
  public ResponseEntity<Void> removeMember(@PathVariable Long groupId, @PathVariable String email,
      Authentication authentication) {
    groupService.removeMember(groupId, email, authentication.getName());
    return ResponseEntity.noContent().build();
  }
}
