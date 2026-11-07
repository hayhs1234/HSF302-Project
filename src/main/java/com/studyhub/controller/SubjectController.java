package com.studyhub.controller;

import com.studyhub.model.Subject;
import com.studyhub.service.SubjectService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/subjects")
public class SubjectController {

    private final SubjectService subjectService;

    public SubjectController(SubjectService subjectService) {
        this.subjectService = subjectService;
    }

    @GetMapping
    public String listSubjects(Model model) {
        model.addAttribute("subjects", subjectService.getAllSubjects());
        model.addAttribute("newSubject", new Subject());
        return "subjects/list";
    }

    @PostMapping("/save")
    public String saveSubject(@Valid @ModelAttribute("newSubject") Subject subject,
                              BindingResult bindingResult,
                              RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Vui lòng nhập đúng thông tin môn học!");
            return "redirect:/subjects";
        }

        subjectService.saveSubject(subject);
        redirectAttributes.addFlashAttribute("successMessage", "Thêm môn học thành công!");
        return "redirect:/subjects";
    }
}

