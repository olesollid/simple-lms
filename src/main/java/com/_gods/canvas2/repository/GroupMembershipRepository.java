package com._gods.canvas2.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com._gods.canvas2.model.Course;
import com._gods.canvas2.model.Group;
import com._gods.canvas2.model.GroupMembership;
import com._gods.canvas2.model.User;

public interface GroupMembershipRepository extends JpaRepository<GroupMembership, Long> {
  List<GroupMembership> findByGroup(Group group);

  List<GroupMembership> findByUser(User user);

  List<GroupMembership> findByUserAndGroupCourse(User user, Course course);

  boolean existsByUser(User user);
}
