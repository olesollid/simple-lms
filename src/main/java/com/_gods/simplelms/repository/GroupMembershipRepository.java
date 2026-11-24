package com._gods.simplelms.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com._gods.simplelms.model.Course;
import com._gods.simplelms.model.Group;
import com._gods.simplelms.model.GroupMembership;
import com._gods.simplelms.model.User;

public interface GroupMembershipRepository extends JpaRepository<GroupMembership, Long> {
  List<GroupMembership> findByGroup(Group group);

  List<GroupMembership> findByUser(User user);

  List<GroupMembership> findByUserAndGroupCourse(User user, Course course);

  boolean existsByUser(User user);
}
